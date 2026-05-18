package net.phylax.credible.aeges;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;

import aeges.v1.Aeges;


/**
 * Transaction pool validator powered by Aeges.
 */
@Slf4j
public class AegesPoolValidator implements PluginTransactionPoolValidator {
    private final AegesGrpcClient client;
    private final LabelledMetric<Counter> verifyOutcomeCounter;

    public AegesPoolValidator(AegesGrpcClient client, LabelledMetric<Counter> verifyOutcomeCounter) {
        this.client = client;
        this.verifyOutcomeCounter = verifyOutcomeCounter;
    }

    @Override
    public Optional<String> validateTransaction(
            Transaction transaction, boolean isLocal, boolean hasPriority) {
        try {
            Aeges.Transaction protoTx = AegesModelConverter.toProtoTransaction(transaction);
            Aeges.VerifyTransactionResponse response = client.verifyTransaction(protoTx);

            if (response == null) {
                verifyOutcomeCounter.labels("error").inc();
                return Optional.empty();
            }

            if (response.getDenied()) {
                verifyOutcomeCounter.labels("denied").inc();
                log.debug("Transaction denied by Aeges: {}", transaction.getHash());
                return Optional.of("AEGES_DENIED");
            }

            verifyOutcomeCounter.labels("allowed").inc();
            return Optional.empty();
        } catch (Exception e) {
            verifyOutcomeCounter.labels("error").inc();
            log.error("Error during Aeges validation for tx {}: {}", transaction.getHash(), e.getMessage(), e);
            return Optional.empty();
        }
    }
}
