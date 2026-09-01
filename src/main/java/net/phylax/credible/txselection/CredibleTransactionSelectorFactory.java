package net.phylax.credible.txselection;

import net.phylax.credible.LineaRuntimeCompatibility.ChainSecurityPolicy;
import net.phylax.credible.metrics.CredibleMetricsRegistry;

import java.util.concurrent.atomic.AtomicLong;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

public class CredibleTransactionSelectorFactory implements PluginTransactionSelectorFactory {
    private final CredibleTransactionSelector.Config txSelectorConfig;
    private final CredibleMetricsRegistry metricsRegistry;
    private AtomicLong iterationId = new AtomicLong(0L);
    private final ChainSecurityPolicy chainSecurityPolicy;
    private final TransactionSelectionResult chainSecurityRuleViolated;

    public CredibleTransactionSelectorFactory(
            final CredibleTransactionSelector.Config txSelectorConfig,
            final CredibleMetricsRegistry metricsRegistry,
            final ChainSecurityPolicy chainSecurityPolicy,
            final TransactionSelectionResult chainSecurityRuleViolated
    ) {
        this.txSelectorConfig = txSelectorConfig;
        this.metricsRegistry = metricsRegistry;
        this.chainSecurityPolicy = chainSecurityPolicy;
        this.chainSecurityRuleViolated = chainSecurityRuleViolated;
    }

    @Override
    public PluginTransactionSelector create(
        final ProcessableBlockHeader pendingBlockHeader,
        final SelectorsStateManager selectorsStateManager) {
        return new CredibleTransactionSelector(
            txSelectorConfig,
            iterationId.incrementAndGet(),
            metricsRegistry,
            chainSecurityPolicy,
            chainSecurityRuleViolated);
    }
}
