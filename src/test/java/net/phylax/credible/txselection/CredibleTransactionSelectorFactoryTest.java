package net.phylax.credible.txselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;

import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.metrics.SimpleMockMetricsSystem;
import net.phylax.credible.strategy.ISidecarStrategy;

import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;
import org.junit.jupiter.api.Test;

class CredibleTransactionSelectorFactoryTest {
    @Test
    void createsDistinctSelectorsWithIncreasingIterationIds() {
        var config = new CredibleTransactionSelector.Config(mock(ISidecarStrategy.class), 0);
        var metrics = new CredibleMetricsRegistry(new SimpleMockMetricsSystem());
        var factory = new CredibleTransactionSelectorFactory(
            config,
            metrics,
            txContext -> false,
            TransactionSelectionResult.invalid("CHAIN_SECURITY_RULE_VIOLATED"));
        var header = mock(ProcessableBlockHeader.class);
        var stateManager = mock(SelectorsStateManager.class);

        var first = (CredibleTransactionSelector) factory.create(header, stateManager);
        var second = (CredibleTransactionSelector) factory.create(header, stateManager);

        assertNotSame(first, second);
        assertEquals(1L, first.getOperationTracer().getCurrentIterationId());
        assertEquals(2L, second.getOperationTracer().getCurrentIterationId());
    }
}
