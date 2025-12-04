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

public class CredibleTransactionSelectorTest {

    // Helper to convert hex string to byte array for tests
    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        String cleanHex = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (cleanHex.length() % 2 != 0) cleanHex = "0" + cleanHex;
        byte[] bytes = new byte[cleanHex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(cleanHex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
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

        mockTransport = new MockTransport(200);
        
        strategy =  new DefaultSidecarStrategy(
            List.of(mockTransport),
            new ArrayList<>(),
            1000,
            metrics);


        var config = new CredibleTransactionSelector.Config(strategy, 0);
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
    public void shouldTimeoutOnSecondIterationButNotFirst() throws InterruptedException {
        // Setup with a short iteration timeout (100ms)
        var metricsSystem = new SimpleMockMetricsSystem();
        var metrics = new CredibleMetricsRegistry(metricsSystem);

        // Processing latency of 50ms per transaction
        var slowTransport = new MockTransport(50);

        var testStrategy = new DefaultSidecarStrategy(
            List.of(slowTransport),
            new ArrayList<>(),
            1000,
            metrics);

        // Iteration timeout of 100ms - enough for ~1-2 transactions
        var configWithTimeout = new CredibleTransactionSelector.Config(testStrategy, 100);
        var factoryWithTimeout = new CredibleTransactionSelectorFactory(configWithTimeout, metrics);

        // First iteration - should complete successfully (processes quickly)
        {
            var selector = (CredibleTransactionSelector) factoryWithTimeout.create(new SelectorsStateManager());
            var operationTracer = selector.getOperationTracer();
            testStrategy.setNewHead("0x00000001", new CommitHead());
            operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(1L), null);

            var evaluationContext = new MockTransactionEvaluationContext("0x1");

            var preResult = selector.evaluateTransactionPreProcessing(evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);

            var postResult = selector.evaluateTransactionPostProcessing(evaluationContext, null);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);

            operationTracer.traceEndBlock(new MockBlockHeader(1L), new MockBlockBody(1));
        }

        // Second iteration - simulate slow processing that exceeds timeout
        {
            var selector = (CredibleTransactionSelector) factoryWithTimeout.create(new SelectorsStateManager());
            var operationTracer = selector.getOperationTracer();
            testStrategy.setNewHead("0x00000002", new CommitHead());
            operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(2L), null);

            // First transaction - should succeed
            var evaluationContext1 = new MockTransactionEvaluationContext("0x1");
            var preResult1 = selector.evaluateTransactionPreProcessing(evaluationContext1);
            assertEquals(TransactionSelectionResult.SELECTED, preResult1);
            var postResult1 = selector.evaluateTransactionPostProcessing(evaluationContext1, null);
            assertEquals(TransactionSelectionResult.SELECTED, postResult1);

            // Sleep to exceed the iteration timeout
            Thread.sleep(150);

            // Second transaction - should be skipped due to timeout (returns SELECTED without processing)
            var evaluationContext2 = new MockTransactionEvaluationContext("0x2");
            var preResult2 = selector.evaluateTransactionPreProcessing(evaluationContext2);
            // Pre-processing returns SELECTED (skipped due to timeout)
            assertEquals(TransactionSelectionResult.SELECTED, preResult2);

            var postResult2 = selector.evaluateTransactionPostProcessing(evaluationContext2, null);
            // Post-processing also returns SELECTED (skipped due to timeout)
            assertEquals(TransactionSelectionResult.SELECTED, postResult2);

            // Third transaction - also skipped
            var evaluationContext3 = new MockTransactionEvaluationContext("0x3");
            var preResult3 = selector.evaluateTransactionPreProcessing(evaluationContext3);
            assertEquals(TransactionSelectionResult.SELECTED, preResult3);
            var postResult3 = selector.evaluateTransactionPostProcessing(evaluationContext3, null);
            assertEquals(TransactionSelectionResult.SELECTED, postResult3);

            operationTracer.traceEndBlock(new MockBlockHeader(2L), new MockBlockBody(1));
        }

        // Third iteration - should work again (new selector, new timer)
        {
            var selector = (CredibleTransactionSelector) factoryWithTimeout.create(new SelectorsStateManager());
            var operationTracer = selector.getOperationTracer();
            testStrategy.setNewHead("0x00000003", new CommitHead());
            operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(3L), null);

            var evaluationContext = new MockTransactionEvaluationContext("0x1");

            var preResult = selector.evaluateTransactionPreProcessing(evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);

            var postResult = selector.evaluateTransactionPostProcessing(evaluationContext, null);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);

            operationTracer.traceEndBlock(new MockBlockHeader(3L), new MockBlockBody(1));
        }
    }

    @Test
    public void shouldUpdateIndexCorrectly() {
        var selector = (CredibleTransactionSelector) factory.create(new SelectorsStateManager());
        final var operationTracer = selector.getOperationTracer();
        strategy.setNewHead("0x00000002", new CommitHead());
        mockTransport.addFailingTx(hexToBytes("0x3"));
        
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
