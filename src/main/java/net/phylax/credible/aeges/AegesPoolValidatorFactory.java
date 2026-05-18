package net.phylax.credible.aeges;

import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidatorFactory;


/**
 * Factory that creates AegesPoolValidator instances.
 */
public class AegesPoolValidatorFactory implements PluginTransactionPoolValidatorFactory {
    private final AegesGrpcClient client;
    private final LabelledMetric<Counter> verifyOutcomeCounter;

    public AegesPoolValidatorFactory(AegesGrpcClient client, LabelledMetric<Counter> verifyOutcomeCounter) {
        this.client = client;
        this.verifyOutcomeCounter = verifyOutcomeCounter;
    }

    @Override
    public PluginTransactionPoolValidator createTransactionValidator() {
        return new AegesPoolValidator(client, verifyOutcomeCounter);
    }
}
