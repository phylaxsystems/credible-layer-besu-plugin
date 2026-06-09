package net.phylax.credible.txselection;

import linea.security.ChainSecurityPolicy;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

import java.util.concurrent.atomic.AtomicLong;

public class CredibleTransactionSelectorFactory implements PluginTransactionSelectorFactory {
    private final CredibleTransactionSelector.Config txSelectorConfig;
    private final CredibleMetricsRegistry metricsRegistry;
    private AtomicLong iterationId = new AtomicLong(0L);
    private final ChainSecurityPolicy chainSecurityPolicy;

    public CredibleTransactionSelectorFactory(
            final CredibleTransactionSelector.Config txSelectorConfig,
            final CredibleMetricsRegistry metricsRegistry,
            final ChainSecurityPolicy chainSecurityPolicy
    ) {
        this.txSelectorConfig = txSelectorConfig;
        this.metricsRegistry = metricsRegistry;
        this.chainSecurityPolicy = chainSecurityPolicy;
    }

    @Override
    public PluginTransactionSelector create(ProcessableBlockHeader pendingBlockHeader, SelectorsStateManager selectorsStateManager) {
        return new CredibleTransactionSelector(txSelectorConfig, iterationId.incrementAndGet(), metricsRegistry, chainSecurityPolicy);
    }

    @SuppressWarnings("Deprecation")
    @Override
    public PluginTransactionSelector create(final SelectorsStateManager selectorsStateManager) {
        return new CredibleTransactionSelector(txSelectorConfig, iterationId.incrementAndGet(), metricsRegistry, chainSecurityPolicy);
    }
}