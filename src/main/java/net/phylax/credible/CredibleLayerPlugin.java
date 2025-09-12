package net.phylax.credible;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.auto.service.AutoService;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import net.phylax.credible.transport.jsonrpc.JsonRpcTransport;
import net.phylax.credible.types.SidecarApiModels.*;
import net.phylax.credible.txselection.*;

/**
 * Plugin for sending BlockEnv to the Credible Layer sidecar
 */
@AutoService(BesuPlugin.class)
public class CredibleLayerPlugin implements BesuPlugin, BesuEvents.BlockAddedListener {
    private static final Logger LOG = LoggerFactory.getLogger(CredibleLayerPlugin.class);
    private static final String PLUGIN_NAME = "credible-sidecar";

    private ServiceManager serviceManager;
    private BesuEvents besuEvents;
    private JsonRpcTransport sidecarClient;
    private TransactionSelectionService transactionSelectionService;
    
    @CommandLine.Command(
        name = PLUGIN_NAME,
        description = "Configuration options for CredibleBlockPlugin",
        mixinStandardHelpOptions = false
    )
    public static class CrediblePluginConfiguration {
        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-enabled"},
            description = "Enable the plugin (default: ${DEFAULT-VALUE})",
            defaultValue = "true",
            arity = "0..1"
        )
        private Boolean enabled = true;
        
        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-rpc-endpoint"},
            description = "RPC endpoint URL for external calls",
            paramLabel = "<url>"
        )
        private String rpcEndpoint;

        @CommandLine.Option(
            names = {"--plugin-credible-sidecar-processing-timeout-ms"},
            description = "Timeout in ms for the Sidecar RPC when waiting for the processing of getTransactions",
            defaultValue = "50"
        )
        private int processingTimeout = 50;

        public String getRpcEndpoint() { return rpcEndpoint; }
        public int getProcessingTimeout() { return processingTimeout; }
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
            
        // Register CLI options
        Optional<PicoCLIOptions> cmdlineOptions = serviceManager.getService(PicoCLIOptions.class);
        if (cmdlineOptions.isPresent()) {
            cmdlineOptions.get().addPicoCLIOptions(PLUGIN_NAME, config);
            LOG.info("CLI options are available");
        } else {
            LOG.error("PicoCLI not available");
        } 
    }

    public static Optional<CrediblePluginConfiguration> pluginConfiguration() {
        return config == null ? Optional.empty() : Optional.of(config);
    }
        
    @Override
    public void start() {
        LOG.info("Starting plugin with connection to RPC {}", config.getRpcEndpoint());

        serviceManager
            .getService(BesuEvents.class)
            .ifPresentOrElse(this::startEvents, () -> LOG.error("BesuEvents service not available"));

        this.sidecarClient = new JsonRpcTransport.Builder()
            .baseUrl(config.getRpcEndpoint())
            .build();

        var credibleTxConfig = new CredibleTransactionSelector.Config(this.sidecarClient, config.getProcessingTimeout());
        
        transactionSelectionService.registerPluginTransactionSelectorFactory(
            new CredibleTransactionSelectorFactory(credibleTxConfig)
        );
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
            .ifPresentOrElse(this::stopEvents, () -> LOG.error("Error retrieving BesuEvents service"));
    }
        
    @Override
    public void onBlockAdded(final AddedBlockContext block) {
        var blockHeader = block.getBlockHeader();

        String blockHash = blockHeader.getBlockHash().toHexString();
        long blockNumber = blockHeader.getNumber();
        
        LOG.debug("Processing new block - Hash: {}, Number: {}", blockHash, blockNumber);
        
        // NOTE: maybe move to some converter
        SendBlockEnvRequest blockEnv = new SendBlockEnvRequest(
            blockHeader.getNumber(),
            blockHeader.getCoinbase().toHexString(),
            blockHeader.getTimestamp(),
            blockHeader.getGasLimit(),
            blockHeader.getBaseFee().map(quantity -> quantity.getAsBigInteger().longValue()).orElse(1L), // 1 Gwei
            blockHeader.getDifficulty().toString(),
            blockHeader.getMixHash().toHexString(),
            new BlobExcessGasAndPrice(0L, 1L)
        );

        try {
            Map<String, Object> response = this.sidecarClient.call("sendBlockEnv", blockEnv, new TypeReference<Map<String, Object>>() {});
                LOG.debug("Sidecar response {}", response);
            } catch (JsonRpcTransport.JsonRpcException e) {
                LOG.debug("JsonRpcException for {}: {}: {}", blockHash, e.getMessage(), e.getError());
            } catch (Exception e) {
                LOG.error("Error handling sendBlockEnv {}", e.getMessage());
            }
    }
}