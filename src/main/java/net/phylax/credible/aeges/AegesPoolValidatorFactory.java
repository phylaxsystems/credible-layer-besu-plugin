package net.phylax.credible.aeges;

import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidatorFactory;


/**
 * Factory that creates AegesPoolValidator instances.
 */
public class AegesPoolValidatorFactory implements PluginTransactionPoolValidatorFactory {
    private final AegesGrpcClient client;

    public AegesPoolValidatorFactory(AegesGrpcClient client) {
        this.client = client;
    }

    @Override
    public PluginTransactionPoolValidator createTransactionValidator() {
        return new AegesPoolValidator(client);
    }
}
