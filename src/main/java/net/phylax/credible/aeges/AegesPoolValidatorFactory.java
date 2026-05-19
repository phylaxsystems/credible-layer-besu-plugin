package net.phylax.credible.aeges;

import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidatorFactory;

import net.phylax.credible.metrics.CredibleMetricsRegistry;


/**
 * Factory that creates AegesPoolValidator instances.
 */
public class AegesPoolValidatorFactory implements PluginTransactionPoolValidatorFactory {
    private final AegesGrpcClient client;
    private final CredibleMetricsRegistry metricsRegistry;

    public AegesPoolValidatorFactory(AegesGrpcClient client, CredibleMetricsRegistry metricsRegistry) {
        this.client = client;
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public PluginTransactionPoolValidator createTransactionValidator() {
        return new AegesPoolValidator(client, metricsRegistry);
    }
}
