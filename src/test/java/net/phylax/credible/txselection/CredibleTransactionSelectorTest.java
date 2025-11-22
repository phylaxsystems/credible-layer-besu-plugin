package net.phylax.credible.txselection;

import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.metrics.SimpleMockMetricsSystem;
import net.phylax.credible.strategy.DefaultSidecarStrategy;
import net.phylax.credible.strategy.ISidecarStrategy;
import net.phylax.credible.transport.MockTransport;
import net.phylax.credible.types.SidecarApiModels.CommitHead;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.List;

import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;

public class CredibleTransactionSelectorTest {
    private CredibleTransactionSelectorFactory factory = null;
    private MockTransport mockTransport = null;
    private ISidecarStrategy strategy = null;

    private TransactionSelectionResult simulatePreProcessing(CredibleTransactionSelector selector, MockTransactionEvaluationContext evaluationContext) {
        var preResult = selector.evaluateTransactionPreProcessing(evaluationContext);
        if (preResult != TransactionSelectionResult.SELECTED) {
            selector.onTransactionNotSelected(evaluationContext, preResult);
        }
        return preResult;
    }

    private TransactionSelectionResult simulatePostProcessing(CredibleTransactionSelector selector, MockTransactionEvaluationContext evaluationContext) {
        var postResult = selector.evaluateTransactionPostProcessing(evaluationContext, null);
        if (postResult != TransactionSelectionResult.SELECTED) {
            selector.onTransactionNotSelected(evaluationContext, postResult);
        }
        return postResult;
    }

    @BeforeEach
    void setup() {
        var metricsSystem = new SimpleMockMetricsSystem();
        var metrics = new CredibleMetricsRegistry(metricsSystem);

        var openTelemetry = OpenTelemetry.noop();
        mockTransport = new MockTransport(200);
        
        strategy =  new DefaultSidecarStrategy(
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

    @Test
    public void shouldUpdateIndexCorrectly() {
        var selector = (CredibleTransactionSelector) factory.create(new SelectorsStateManager());
        final var operationTracer = selector.getOperationTracer();
        strategy.setNewHead("0x00000002", new CommitHead());
        mockTransport.addFailingTx("0x3");
        
        operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(1L), null);

        /// We simulate 4 transactions, 3rd one triggers an assertion
        /// The index is the number of transactions the selector processed or queued for processing
        /// and it's incremented in the preProcessing

        // TX1 ok
        {
            assertEquals(selector.getCurrentIndex(), 0);

            var evaluationContext = new MockTransactionEvaluationContext("0x1");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            
            assertEquals(preResult, TransactionSelectionResult.SELECTED);
            assertEquals(selector.getCurrentIndex(), 1);

            var postResult = simulatePostProcessing(selector, evaluationContext);
            
            assertEquals(postResult, TransactionSelectionResult.SELECTED);
        }

        // TX2 ok
        {
            assertEquals(selector.getCurrentIndex(), 1);

            var evaluationContext = new MockTransactionEvaluationContext("0x2");
            var preResult = simulatePreProcessing(selector, evaluationContext);

            assertEquals(preResult, TransactionSelectionResult.SELECTED);
            assertEquals(selector.getCurrentIndex(), 2);
            
            var postResult = simulatePostProcessing(selector, evaluationContext);
            
            assertEquals(postResult, TransactionSelectionResult.SELECTED);
        }

        // TX3 assertion_failed
        {
            assertEquals(selector.getCurrentIndex(), 2);

            var evaluationContext = new MockTransactionEvaluationContext("0x3");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            if (preResult != TransactionSelectionResult.SELECTED) {
                selector.onTransactionNotSelected(evaluationContext, preResult);
            }

            assertEquals(preResult, TransactionSelectionResult.SELECTED);
            assertEquals(selector.getCurrentIndex(), 3);
            
            var postResult = simulatePostProcessing(selector, evaluationContext);
            
            assertNotEquals(postResult, TransactionSelectionResult.SELECTED);
        }

        // TX4 ok
        {
            assertEquals(selector.getCurrentIndex(), 2);

            var evaluationContext = new MockTransactionEvaluationContext("0x4");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            if (preResult != TransactionSelectionResult.SELECTED) {
                selector.onTransactionNotSelected(evaluationContext, preResult);
            }

            assertEquals(preResult, TransactionSelectionResult.SELECTED);
            assertEquals(selector.getCurrentIndex(), 3);
            
            var postResult = simulatePostProcessing(selector, evaluationContext);
            
            assertEquals(postResult, TransactionSelectionResult.SELECTED);
            assertEquals(selector.getCurrentIndex(), 3);
        }
        

        
        operationTracer.traceEndBlock(new MockBlockHeader(1L), new MockBlockBody(1));
    }
}
