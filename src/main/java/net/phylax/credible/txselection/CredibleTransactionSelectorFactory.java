package net.phylax.credible.txselection;

import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

import net.phylax.credible.metrics.CredibleMetricsRegistry;

import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;

public class CredibleTransactionSelectorFactory implements PluginTransactionSelectorFactory {
    private final CredibleTransactionSelector.Config txSelectorConfig;
    private final CredibleMetricsRegistry metricsRegistry;

    public CredibleTransactionSelectorFactory(
        final CredibleTransactionSelector.Config txSelectorConfig,
        final CredibleMetricsRegistry metricsRegistry) {
        this.txSelectorConfig = txSelectorConfig;
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public PluginTransactionSelector create(final SelectorsStateManager selectorsStateManager) {
        return new CredibleTransactionSelector(txSelectorConfig, metricsRegistry);
    }
}