package net.phylax.credible.txselection;

import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;

public class CredibleTransactionSelectorFactory implements PluginTransactionSelectorFactory {
    private final CredibleTransactionSelector.Config txSelectorConfig;

    public CredibleTransactionSelectorFactory(
        final CredibleTransactionSelector.Config txSelectorConfig) {
        this.txSelectorConfig = txSelectorConfig;
    }

    @Override
    public PluginTransactionSelector create(final SelectorsStateManager selectorsStateManager) {
        return new CredibleTransactionSelector(txSelectorConfig);
    }
}