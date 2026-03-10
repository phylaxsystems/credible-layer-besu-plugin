package net.phylax.credible.aeges;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;

import aeges.v1.Aeges;


/**
 * Transaction pool validator that checks incoming transactions against the
 * Aeges deny cache via a bidirectional gRPC stream.
 *
 * Fail-open: if the Aeges service is unavailable or times out, transactions
 * are allowed through.
 */
@Slf4j
public class AegesPoolValidator implements PluginTransactionPoolValidator {
    private final AegesGrpcClient client;

    public AegesPoolValidator(AegesGrpcClient client) {
        this.client = client;
    }

    @Override
    public Optional<String> validateTransaction(
            Transaction transaction, boolean isLocal, boolean hasPriority) {
        try {
            Aeges.Transaction protoTx = AegesModelConverter.toProtoTransaction(transaction);
            Aeges.VerifyTransactionResponse response = client.verifyTransaction(protoTx);

            if (response == null) {
                // Fail-open: service unavailable
                return Optional.empty();
            }

            if (response.getDenied()) {
                log.debug("Transaction denied by Aeges: {}", transaction.getHash());
                return Optional.of("AEGES_DENIED");
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Error during Aeges validation for tx {}: {}", transaction.getHash(), e.getMessage(), e);
            // Fail-open on any unexpected error
            return Optional.empty();
        }
    }
}
