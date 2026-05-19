package net.phylax.credible.aeges;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;

import aeges.v1.Aeges;
import net.phylax.credible.metrics.CredibleMetricsRegistry;


/**
 * Transaction pool validator powered by Aeges.
 */
@Slf4j
public class AegesPoolValidator implements PluginTransactionPoolValidator {
    private final AegesGrpcClient client;
    private final CredibleMetricsRegistry metricsRegistry;

    public AegesPoolValidator(AegesGrpcClient client, CredibleMetricsRegistry metricsRegistry) {
        this.client = client;
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public Optional<String> validateTransaction(
            Transaction transaction, boolean isLocal, boolean hasPriority) {
        try {
            Aeges.Transaction protoTx = AegesModelConverter.toProtoTransaction(transaction);
            Aeges.VerifyTransactionResponse response = client.verifyTransaction(protoTx);

            if (response == null) {
                // Service unavailable
                metricsRegistry.getAegesVerifyOutcomeCounter().labels("error").inc();
                return Optional.empty();
            }

            if (response.getDenied()) {
                metricsRegistry.getAegesVerifyOutcomeCounter().labels("denied").inc();
                log.debug("Transaction denied by Aeges: {}", transaction.getHash());
                return Optional.of("AEGES_DENIED");
            }

            metricsRegistry.getAegesVerifyOutcomeCounter().labels("allowed").inc();
            return Optional.empty();
        } catch (Exception e) {
            metricsRegistry.getAegesVerifyOutcomeCounter().labels("error").inc();
            log.error("Error during Aeges validation for tx {}: {}", transaction.getHash(), e.getMessage(), e);
            // Unexpected error
            return Optional.empty();
        }
    }
}
