package net.phylax.credible;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.data.AddedBlockContext.EventType;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.metrics.MetricCategoryRegistry;

import com.google.auto.service.AutoService;

import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.metrics.CredibleMetricsCategory;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.strategy.DefaultSidecarStrategy;
import net.phylax.credible.strategy.ISidecarStrategy;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.transport.grpc.GrpcTransport;
import net.phylax.credible.transport.jsonrpc.JsonRpcTransport;
import net.phylax.credible.txselection.CredibleTransactionSelector;
import net.phylax.credible.txselection.CredibleTransactionSelectorFactory;
import net.phylax.credible.types.SidecarApiModels.CommitHead;
import picocli.CommandLine;

/**
 * Plugin for sending BlockEnv to the Credible Layer sidecar
 */
@AutoService(BesuPlugin.class)
@Slf4j
public class CredibleLayerPlugin implements BesuPlugin, BesuEvents.BlockAddedListener {
    private static final String PLUGIN_NAME = "credible-sidecar";

    private ServiceManager serviceManager;
    private BesuEvents besuEvents;
    private MetricsSystem metricsSystem;
    private TransactionSelectionService transactionSelectionService;
    private ISidecarStrategy strategy;

    private CredibleMetricsRegistry metricsRegistry;
    
    // Keeps track of the last (block_hash, block_number) sent
    private String lastBlockSent = "";

    @CommandLine.Command(
        name = PLUGIN_NAME,
        description = "Configuration options for CredibleBlockPlugin",
        mixinStandardHelpOptions = false
    )
    public static class CrediblePluginConfiguration {
        public enum TransportType {
            HTTP,
            GRPC
        }

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-transport-type"},
            description = "Transport type for the Credible sidecar (http, grpc)",
            defaultValue = "http",
            converter = TransportTypeConverter.class
        )
        private TransportType transportType = TransportType.HTTP;

        static class TransportTypeConverter implements CommandLine.ITypeConverter<TransportType> {
            @Override
            public TransportType convert(String value) {
                if (value == null) {
                    throw new CommandLine.TypeConversionException("Transport type cannot be null");
                }

                try {
                    return TransportType.valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new CommandLine.TypeConversionException(
                        "Invalid transport type '" + value + "'. Expected one of: http, grpc.");
                }
            }
        }

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-rpc-endpoints"},
            description = "List of RPC endpoints to connect to the Credible sidecars",
            paramLabel = "<url>",
            split = ","
        )
        private List<String> rpcEndpoints;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-rpc-fallback-endpoints"},
            description = "List of fallback RPC endpoints",
            paramLabel = "<url>",
            split = ","
        )
        private List<String> fallbackEndpoints;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-read-timeout-ms"},
            description = "Request timeout in ms for any request to the Sidecar RPC",
            defaultValue = "800"
        )
        private int readTimeout = 800;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-write-timeout-ms"},
            description = "Request timeout in ms for any request to the Sidecar RPC",
            defaultValue = "800"
        )
        private int writeTimeout = 800;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-processing-timeout-ms"},
            description = "Timeout in ms for the Sidecar RPC when waiting for the processing of getTransactions",
            defaultValue = "300"
        )
        private int processingTimeout = 300;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-aggregated-timeout-ms"},
            description = "Maximum time in ms allowed to spend in pre and post processing within an iteration (block building). When exceeded, the strategy becomes inactive for the remainder of block production.",
            defaultValue = "2000"
        )
        private int aggregatedTimeout = 2000;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-grpc-endpoints"},
            description = "List of gRPC endpoints (format: host:port) - mutually exclusive with rpc-endpoints",
            paramLabel = "<host:port>",
            split = ","
        )
        private List<String> grpcEndpoints = new ArrayList<>();

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-grpc-fallback-endpoints"},
            description = "List of fallback gRPC endpoints (format: host:port)",
            paramLabel = "<host:port>",
            split = ","
        )
        private List<String> grpcFallbackEndpoints =  new ArrayList<>();

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-otel-endpoint"},
            description = "Tracing HTTP endpoint"
        )
        private String otelEndpoint = null;

        public List<String> getRpcEndpoints() { return rpcEndpoints; }
        public List<String> getFallbackEndpoints() { return fallbackEndpoints; }
        public List<String> getGrpcEndpoints() { return grpcEndpoints; }
        public List<String> getGrpcFallbackEndpoints() { return grpcFallbackEndpoints; }
        public int getProcessingTimeout() { return processingTimeout; }
        public int getAggregatedTimeout() { return aggregatedTimeout; }
        public int getReadTimeout() { return readTimeout; }
        public int getWriteTimeout() { return writeTimeout; }
        public TransportType getTransportType() { return transportType; }
        public String getOtelEndpoint() { return otelEndpoint; }
    }

    private static CrediblePluginConfiguration config = null;

    @Override
    public void register(final ServiceManager serviceManager) {
        this.serviceManager = serviceManager;

        config = new CrediblePluginConfiguration();

        transactionSelectionService =
            serviceManager
                .getService(TransactionSelectionService.class)
                .orElseThrow(
                    () ->
                        new RuntimeException(
                            "Failed to obtain TransactionSelectionService from the ServiceManager."));

        // Register metrics category
        var metricsCategoryRegistry = serviceManager.getService(MetricCategoryRegistry.class)
                .orElseThrow(
                    () -> 
                        new RuntimeException("Failed to obtain MetricCategoryRegistry from the ServiceManager."));
        metricsCategoryRegistry.addMetricCategory(CredibleMetricsCategory.PLUGIN);
            
        // Register CLI options
        Optional<PicoCLIOptions> cmdlineOptions = serviceManager.getService(PicoCLIOptions.class);
        if (cmdlineOptions.isPresent()) {
            cmdlineOptions.get().addPicoCLIOptions(PLUGIN_NAME, config);
            log.info("CLI options are available");
        } else {
            log.error("PicoCLI not available");
        }
    }


    public static Optional<CrediblePluginConfiguration> pluginConfiguration() {
        return config == null ? Optional.empty() : Optional.of(config);
    }
        
    @Override
    public void start() {
        // Validate configuration: the selected transport must be configured
        validateTransportConfiguration();

        // Determine which transport type should be used
        var transportType = config.getTransportType();
        boolean isJsonRpc = transportType == CrediblePluginConfiguration.TransportType.HTTP;

        if (isJsonRpc) {
            log.info(
                "Starting plugin with JSON-RPC transport to {}: readTimeout {}, writeTimeout {}, processingTimeout {}",
                String.join(", ", config.getRpcEndpoints()),
                config.getReadTimeout(),
                config.getWriteTimeout(),
                config.getProcessingTimeout());
        } else {
            log.info(
                "Starting plugin with gRPC transport to {}: deadlineTimeout {}, processingTimeout {}",
                String.join(", ", config.getGrpcEndpoints()),
                config.getReadTimeout(),
                config.getProcessingTimeout());
        }

        serviceManager
            .getService(BesuEvents.class)
            .ifPresentOrElse(this::startEvents, () -> log.error("BesuEvents service not available"));

        // Initialize the metrics system
        this.metricsSystem = serviceManager.getService(MetricsSystem.class)
            .orElseThrow(
                () ->
                    new RuntimeException("Failed to obtain MetricsSystem from the ServiceManager."));

        metricsRegistry = new CredibleMetricsRegistry(metricsSystem);

        // Create transports based on configuration
        List<ISidecarTransport> primaryTransports;
        List<ISidecarTransport> fallbackTransports;

        if (isJsonRpc) {
            primaryTransports = createJsonRpcTransports(config.getRpcEndpoints());
            fallbackTransports = createJsonRpcTransports(config.getFallbackEndpoints());
        } else {
            primaryTransports = createGrpcTransports(config.getGrpcEndpoints());
            fallbackTransports = createGrpcTransports(config.getGrpcFallbackEndpoints());
        }

        strategy = new DefaultSidecarStrategy(primaryTransports, fallbackTransports, config.getProcessingTimeout(), metricsRegistry);

        var credibleTxConfig = new CredibleTransactionSelector.Config(strategy, config.getAggregatedTimeout());

        transactionSelectionService.registerPluginTransactionSelectorFactory(
            new CredibleTransactionSelectorFactory(credibleTxConfig, metricsRegistry)
        );
    }

    /**
     * Validate that the selected transport type has the required endpoints configured.
     */
    private void validateTransportConfiguration() {
        boolean hasJsonRpc = isNotEmpty(config.getRpcEndpoints());
        boolean hasGrpc = isNotEmpty(config.getGrpcEndpoints());
        var transportType = config.getTransportType();

        switch (transportType) {
            case HTTP:
                if (!hasJsonRpc) {
                    throw new IllegalStateException(
                        "Transport type HTTP selected but no RPC endpoints configured. Please specify:\n" +
                        "  --plugin-credible-sidecar-rpc-endpoints"
                    );
                }
                if (hasGrpc) {
                    log.warn("gRPC endpoints are configured but transport type is HTTP; the gRPC endpoints will be ignored");
                }
                break;
            case GRPC:
                if (!hasGrpc) {
                    throw new IllegalStateException(
                        "Transport type gRPC selected but no gRPC endpoints configured. Please specify:\n" +
                        "  --plugin-credible-sidecar-grpc-endpoints"
                    );
                }
                if (hasJsonRpc) {
                    log.warn("JSON-RPC endpoints are configured but transport type is gRPC; the JSON-RPC endpoints will be ignored");
                }
                break;
            default:
                log.warn("No transport type has been selected!");
        }

        if (!hasJsonRpc && !hasGrpc) {
            log.warn("No transport endpoints configured, plugin will be disabled.");
        }
    }

    /**
     * Create JSON-RPC transports from endpoint URLs
     */
    private List<ISidecarTransport> createJsonRpcTransports(List<String> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return List.of();
        }
        return endpoints.stream()
            .map(endpoint -> new JsonRpcTransport.Builder()
                    .readTimeout(Duration.ofMillis(config.getReadTimeout()))
                    .writeTimeout(Duration.ofMillis(config.getWriteTimeout()))
                    .metricsRegistry(metricsRegistry)
                    .baseUrl(endpoint)
                    .build())
            .collect(Collectors.toList());
    }

    /**
     * Create gRPC transports from host:port endpoints
     */
    private List<ISidecarTransport> createGrpcTransports(List<String> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return List.of();
        }
        return endpoints.stream()
            .map(endpoint -> {
                String[] parts = endpoint.split(":");
                if (parts.length != 2) {
                    throw new IllegalArgumentException(
                        "Invalid gRPC endpoint format: " + endpoint +
                        ". Expected format: host:port (e.g., localhost:50051)"
                    );
                }
                String host = parts[0];
                int port;
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "Invalid port number in gRPC endpoint: " + endpoint +
                        ". Port must be a number."
                    );
                }
                return new GrpcTransport.Builder()
                    .host(host)
                    .port(port)
                    .metricsRegistry(metricsRegistry)
                    .deadlineMillis(config.getReadTimeout())
                    .build();
            })
            .collect(Collectors.toList());
    }

    /**
     * Helper to check if a list is not null and not empty
     */
    private boolean isNotEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private long listenerIdentifier;

    private void startEvents(final BesuEvents events) {
        listenerIdentifier = events.addBlockAddedListener(this::onBlockAdded);
    }

    private void stopEvents(final BesuEvents events) {
        events.removeBlockAddedListener(listenerIdentifier);
    }

    @Override
    public void stop() {
        serviceManager
            .getService(BesuEvents.class)
            .ifPresentOrElse(this::stopEvents, () -> log.error("Error retrieving BesuEvents service"));
    }
        
    @Override
    public void onBlockAdded(final AddedBlockContext block) {
        if (block.getEventType() != EventType.HEAD_ADVANCED) {
            log.debug("Skipping onBlockAdded, event type: {}", block.getEventType());
            return;
        }
        var blockHeader = block.getBlockHeader();
        var blockBody = block.getBlockBody();
        var transactions = blockBody.getTransactions();

        String blockHash = blockHeader.getBlockHash().toHexString();
        long blockNumber = blockHeader.getNumber();

        try {
            // Check if we sent the block already
            if (blockHash.equals(lastBlockSent)) {
                log.debug("Block already sent - Hash: {}, Number: {}", blockHash, blockNumber);
                return;
            }

            // Validates if the block is valid for sending it to the Credible Layer
            validateBlock(block);

            log.debug("Processing new block - Hash: {}, Number: {}", blockHash, blockNumber);

            // Get transaction information from the actual block
            int transactionCount = transactions.size();
            byte[] lastTxHash = null;

            if (!transactions.isEmpty()) {
                lastTxHash = transactions.get(transactions.size() - 1).getHash().toArrayUnsafe();
            }

            byte[] parentBlockHash = blockHeader.getParentHash().toArrayUnsafe();

            byte[] parentBeaconBlockRoot = blockHeader.getParentBeaconBlockRoot()
                .map(root -> root.toArrayUnsafe())
                .orElse(null);

            long timestamp = blockHeader.getTimestamp();

            // NOTE: iterationId will be overwritten once sent
            CommitHead newHead = new CommitHead(
                lastTxHash,
                transactionCount,
                blockNumber,
                0L,
                parentBlockHash,
                parentBeaconBlockRoot,
                timestamp
            );

            this.strategy.setNewHead(blockHash, newHead);
            lastBlockSent = blockHash;

            log.debug("Block Env sent, hash: {}", blockHash);
        } catch (Exception e) {
            log.error("Error handling sendBlockEnv {}", e.getMessage());
        }
    }

    private void validateBlock(AddedBlockContext block) {
        if (!block.getBlockHeader().getBaseFee().isPresent()) {
            throw new IllegalStateException("Block base fee is not present");
        }
    }
}
