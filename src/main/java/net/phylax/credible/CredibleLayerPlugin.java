package net.phylax.credible;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.data.AddedBlockContext.EventType;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.TransactionPoolValidatorService;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.metrics.MetricCategoryRegistry;

import com.google.auto.service.AutoService;
import io.grpc.ManagedChannel;

import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.aeges.AegesGrpcClient;
import net.phylax.credible.aeges.AegesPoolValidatorFactory;
import net.phylax.credible.metrics.CredibleMetricsCategory;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.strategy.DefaultSidecarStrategy;
import net.phylax.credible.strategy.ISidecarStrategy;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.transport.grpc.BaseGrpcTransport;
import net.phylax.credible.transport.grpc.GrpcTransport;
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
    private TransactionPoolValidatorService transactionPoolValidatorService;
    private ISidecarStrategy strategy;

    private CredibleMetricsRegistry metricsRegistry;

    // Aeges client (null if Aeges is not configured)
    private AegesGrpcClient aegesClient;

    // Keeps track of the last (block_hash, block_number) sent
    private String lastBlockSent = "";

    @CommandLine.Command(
        name = PLUGIN_NAME,
        description = "Configuration options for CredibleBlockPlugin",
        mixinStandardHelpOptions = false
    )
    public static class CrediblePluginConfiguration {
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
            names = {"--plugin-credible-sidecar-commit-head-timeout-ms"},
            description = "Timeout in ms for the sidecar when waiting for the processing of commitHead",
            defaultValue = "50"
        )
        private int commitHeadTimeout = 50;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-poll-interval-ms"},
            description = "Poll interval in ms for the getTransaction fallback polling loop",
            defaultValue = "10"
        )
        private long pollIntervalMs = 10;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-grpc-endpoints"},
            description = "List of gRPC endpoints (format: host:port)",
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

        // Aeges options
        @CommandLine.Option(
            names = {"--plugin-credible-aeges-grpc-endpoints"},
            description = "Aeges gRPC endpoints (format: host:port). When set, enables the Aeges transaction pool validator.",
            paramLabel = "<host:port>",
            split = ","
        )
        private List<String> aegesGrpcEndpoints = new ArrayList<>();

        @CommandLine.Option(
            names = {"--plugin-credible-aeges-deadline-ms"},
            description = "gRPC deadline in milliseconds for each Aeges verification call",
            defaultValue = "500"
        )
        private long aegesDeadlineMillis = 500;

        public List<String> getGrpcEndpoints() { return grpcEndpoints; }
        public List<String> getGrpcFallbackEndpoints() { return grpcFallbackEndpoints; }
        public int getProcessingTimeout() { return processingTimeout; }
        public int getAggregatedTimeout() { return aggregatedTimeout; }
        public int getReadTimeout() { return readTimeout; }
        public int getWriteTimeout() { return writeTimeout; }
        public String getOtelEndpoint() { return otelEndpoint; }
        public int getCommitHeadTimeout() { return commitHeadTimeout; }
        public List<String> getAegesGrpcEndpoints() { return aegesGrpcEndpoints; }
        public long getAegesDeadlineMillis() { return aegesDeadlineMillis; }
        public long getPollIntervalMs() { return pollIntervalMs; }
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

        // Obtain TransactionPoolValidatorService for Aeges (optional — only needed when Aeges is configured)
        transactionPoolValidatorService =
            serviceManager
                .getService(TransactionPoolValidatorService.class)
                .orElse(null);

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
        // Validate configuration: gRPC endpoints must be configured
        validateTransportConfiguration();

        log.info(
            "Starting plugin with gRPC transport to {}: deadlineTimeout {}, processingTimeout {}",
            String.join(", ", config.getGrpcEndpoints()),
            config.getReadTimeout(),
            config.getProcessingTimeout());

        besuEvents = serviceManager
            .getService(BesuEvents.class)
            .orElseThrow(() -> new RuntimeException("BesuEvents service not available"));

        startEvents(besuEvents);

        // Initialize the metrics system
        this.metricsSystem = serviceManager.getService(MetricsSystem.class)
            .orElseThrow(
                () ->
                    new RuntimeException("Failed to obtain MetricsSystem from the ServiceManager."));

        metricsRegistry = new CredibleMetricsRegistry(metricsSystem);

        // Create transports based on configuration
        List<ISidecarTransport> primaryTransports;
        List<ISidecarTransport> fallbackTransports;

        primaryTransports = createGrpcTransports(config.getGrpcEndpoints());
        fallbackTransports = createGrpcTransports(config.getGrpcFallbackEndpoints());

        strategy = new DefaultSidecarStrategy(primaryTransports, fallbackTransports, config.getProcessingTimeout(), config.getPollIntervalMs(), metricsRegistry);

        var credibleTxConfig = new CredibleTransactionSelector.Config(strategy, config.getAggregatedTimeout());

        transactionSelectionService.registerPluginTransactionSelectorFactory(
            new CredibleTransactionSelectorFactory(credibleTxConfig, metricsRegistry)
        );

        // Start Aeges pool validator if configured
        startAeges();
    }

    /**
     * Validate that gRPC endpoints are configured.
     */
    private void validateTransportConfiguration() {
        if (!isNotEmpty(config.getGrpcEndpoints())) {
            throw new IllegalStateException(
                "No gRPC endpoints configured. Please specify:\n" +
                "  --plugin-credible-sidecar-grpc-endpoints"
            );
        }
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
     * Start the Aeges pool validator if endpoints are configured.
     */
    private void startAeges() {
        if (!isNotEmpty(config.getAegesGrpcEndpoints())) {
            log.info("Aeges not configured, skipping pool validator registration");
            return;
        }

        if (transactionPoolValidatorService == null) {
            log.warn("Aeges endpoints configured but TransactionPoolValidatorService is not available, skipping");
            return;
        }

        // Use the first configured endpoint
        String endpoint = config.getAegesGrpcEndpoints().get(0);
        String[] parts = endpoint.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                "Invalid Aeges gRPC endpoint format: " + endpoint +
                ". Expected format: host:port (e.g., localhost:50051)");
        }

        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid port in Aeges gRPC endpoint: " + endpoint);
        }

        log.info("Starting Aeges pool validator with endpoint {}:{}, deadline {}ms",
            host, port, config.getAegesDeadlineMillis());

        ManagedChannel channel = new BaseGrpcTransport.ChannelBuilder()
            .host(host)
            .port(port)
            .build();

        aegesClient = new AegesGrpcClient(channel, config.getAegesDeadlineMillis());

        transactionPoolValidatorService.registerPluginTransactionValidatorFactory(
            new AegesPoolValidatorFactory(aegesClient));

        log.info("Aeges pool validator registered");
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
        stopEvents(besuEvents);

        if (aegesClient != null) {
            log.info("Stopping Aeges client");
            aegesClient.close();
            aegesClient = null;
        }
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

            byte[] blockHashBytes = blockHeader.getBlockHash().toArrayUnsafe();

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
                blockHashBytes,
                parentBeaconBlockRoot,
                timestamp
            );
        
            this.strategy.commitHead(newHead, config.getCommitHeadTimeout());
            lastBlockSent = blockHash;

            log.debug("Block Env sent, hash: {}", blockHash);
        } catch (Exception e) {
            log.error("Error handling commitHead {}", e.getMessage());
        }
    }

    private void validateBlock(AddedBlockContext block) {
        if (!block.getBlockHeader().getBaseFee().isPresent()) {
            throw new IllegalStateException("Block base fee is not present");
        }
    }
}
