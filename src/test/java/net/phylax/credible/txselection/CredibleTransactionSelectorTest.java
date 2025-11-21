package net.phylax.credible.txselection;

import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.metrics.SimpleMockMetricsSystem;
import net.phylax.credible.strategy.DefaultSidecarStrategy;
import net.phylax.credible.transport.MockTransport;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;

public class CredibleTransactionSelectorTest {
    private CredibleTransactionSelectorFactory factory = null;

    @BeforeEach
    void setup() {
        var metricsSystem = new SimpleMockMetricsSystem();
        var metrics = new CredibleMetricsRegistry(metricsSystem);

        var openTelemetry = OpenTelemetry.noop();
        var mockTransport = new MockTransport(200);
        
        var strategy =  new DefaultSidecarStrategy(
            List.of(mockTransport),
            new ArrayList<>(),
            1000,
            metrics,
            openTelemetry.getTracer("default-strategy"));


        var config = new CredibleTransactionSelector.Config(strategy);
        factory = new CredibleTransactionSelectorFactory(config, metrics);
    }

    @Test
    public void shouldProcessSelectorsSuccessfully() {
        for(int i = 0; i < 10; i++) {
            var selector = factory.create(new SelectorsStateManager());
            final var operationTracer = selector.getOperationTracer();
            operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(Long.valueOf(i)), null);

            var evaluationContext = new MockTransactionEvaluationContext("0x1");

            var preResult = selector.evaluateTransactionPreProcessing(evaluationContext);
            assertEquals(preResult, TransactionSelectionResult.SELECTED);
            // Here is the EVM processing
            var postResult = selector.evaluateTransactionPostProcessing(evaluationContext, null);
            assertEquals(postResult, TransactionSelectionResult.SELECTED);

            operationTracer.traceEndBlock(new MockBlockHeader(Long.valueOf(i)), new MockBlockBody(1));
        }
    }
}
