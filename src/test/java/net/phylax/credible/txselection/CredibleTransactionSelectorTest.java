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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    // Helper to generate a CommitHead with realistic test values
    private static CommitHead generateCommitHead(long blockNumber, int nTransactions, long iterationId) {
        // Generate a deterministic last tx hash based on block number
        byte[] lastTxHash = new byte[32];
        for (int i = 0; i < 32; i++) {
            lastTxHash[i] = (byte) ((blockNumber + i) & 0xFF);
        }

        // Generate a deterministic block hash
        byte[] blockHash = new byte[32];
        for (int i = 0; i < 32; i++) {
            blockHash[i] = (byte) ((blockNumber * 2 + i) & 0xFF);
        }

        // Generate a deterministic parent beacon block root
        byte[] parentBeaconBlockRoot = new byte[32];
        for (int i = 0; i < 32; i++) {
            parentBeaconBlockRoot[i] = (byte) ((blockNumber * 3 + i) & 0xFF);
        }

        long timestamp = 1700000000L + blockNumber * 12; // ~12 seconds per block

        return new CommitHead(
            lastTxHash,
            nTransactions,
            blockNumber,
            iterationId,
            blockHash,
            parentBeaconBlockRoot,
            timestamp
        );
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
            strategy.commitHead(generateCommitHead(Long.valueOf(i), i, Long.valueOf(i + 1)), 100);
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
    public void shouldTimeoutOnSecondIterationButNotFirst() {
        // Setup with a short aggregated timeout (100ms)
        var metricsSystem = new SimpleMockMetricsSystem();
        var metrics = new CredibleMetricsRegistry(metricsSystem);

        // Processing latency of 60ms per transaction (pre + post = ~120ms for 1 full tx)
        var slowTransport = new MockTransport(60);

        var testStrategy = new DefaultSidecarStrategy(
            List.of(slowTransport),
            new ArrayList<>(),
            1000,
            metrics);

        // Aggregated timeout of 100ms - only counts time spent in pre/post processing
        // With 60ms per call, first tx will use ~120ms (pre + post), exceeding the 100ms budget
        var configWithTimeout = new CredibleTransactionSelector.Config(testStrategy, 100);
        var factoryWithTimeout = new CredibleTransactionSelectorFactory(configWithTimeout, metrics);

        // First iteration - should complete successfully (only 1 transaction)
        {
            var selector = (CredibleTransactionSelector) factoryWithTimeout.create(new SelectorsStateManager());
            var operationTracer = selector.getOperationTracer();
            testStrategy.commitHead(generateCommitHead(1L, 0, 1L), 100);
            operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(1L), null);

            var evaluationContext = new MockTransactionEvaluationContext("0x1");

            var preResult = selector.evaluateTransactionPreProcessing(evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);

            var postResult = selector.evaluateTransactionPostProcessing(evaluationContext, null);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);

            operationTracer.traceEndBlock(new MockBlockHeader(1L), new MockBlockBody(1));
        }

        // Second iteration - process multiple transactions to exceed aggregated timeout
        {
            var selector = (CredibleTransactionSelector) factoryWithTimeout.create(new SelectorsStateManager());
            var operationTracer = selector.getOperationTracer();
            testStrategy.commitHead(generateCommitHead(2L, 1, 2L), 100);
            operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(2L), null);

            // First transaction - should succeed (uses ~120ms of the 100ms budget, triggering timeout)
            var evaluationContext1 = new MockTransactionEvaluationContext("0x1");
            var preResult1 = selector.evaluateTransactionPreProcessing(evaluationContext1);
            assertEquals(TransactionSelectionResult.SELECTED, preResult1);
            var postResult1 = selector.evaluateTransactionPostProcessing(evaluationContext1, null);
            // Post-processing should trigger timeout since aggregated time exceeds budget
            assertEquals(TransactionSelectionResult.SELECTED, postResult1);

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

            // Third transaction - also skipped
            var evaluationContext4 = new MockTransactionEvaluationContext("0x4");
            var preResult4 = selector.evaluateTransactionPreProcessing(evaluationContext4);
            assertEquals(TransactionSelectionResult.SELECTED, preResult4);
            var postResult4 = selector.evaluateTransactionPostProcessing(evaluationContext4, null);
            assertEquals(TransactionSelectionResult.SELECTED, postResult4);

            operationTracer.traceEndBlock(new MockBlockHeader(2L), new MockBlockBody(1));

            // Strategy should be inactive after timeout
            assertEquals(false, testStrategy.isActive());
        }

        // Third iteration - should work again (new selector, new timer, re-activate strategy)
        {
            var selector = (CredibleTransactionSelector) factoryWithTimeout.create(new SelectorsStateManager());
            var operationTracer = selector.getOperationTracer();
            testStrategy.commitHead(generateCommitHead(3L, 4, 3L), 100);
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
        strategy.commitHead(generateCommitHead(1L, 0, 1L), 100);
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

    @Test
    public void shouldSendReorgOnTransactionNotSelected() throws InterruptedException {
        var selector = (CredibleTransactionSelector) factory.create(new SelectorsStateManager());
        final var operationTracer = selector.getOperationTracer();
        strategy.commitHead(generateCommitHead(1L, 0, 1L), 100);
        mockTransport.clearReorgRequests();

        operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(1L), null);

        // TX1 - success
        {
            var evaluationContext = new MockTransactionEvaluationContext("0x1");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);
            var postResult = simulatePostProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);
        }

        // TX2 - success
        {
            var evaluationContext = new MockTransactionEvaluationContext("0x2");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);
            var postResult = simulatePostProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);
        }

        // TX3 - manually trigger reorg by calling onTransactionNotSelected
        {
            var evaluationContext = new MockTransactionEvaluationContext("0x3");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);
            assertEquals(3, selector.getCurrentIndex());

            // Directly call onTransactionNotSelected to trigger reorg
            selector.onTransactionNotSelected(evaluationContext, TransactionSelectionResult.invalid("TX rejected"));
        }

        // Wait for async reorg request to be sent
        Thread.sleep(100);

        // Verify reorg was sent
        var reorgRequests = mockTransport.getReorgRequests();
        assertEquals(1, reorgRequests.size());

        operationTracer.traceEndBlock(new MockBlockHeader(1L), new MockBlockBody(1));
    }

    @Test
    public void shouldReorgBundleTransactions() throws InterruptedException {
        var selector = (CredibleTransactionSelector) factory.create(new SelectorsStateManager());
        final var operationTracer = selector.getOperationTracer();
        strategy.commitHead(generateCommitHead(1L, 0, 1L), 100);
        mockTransport.clearReorgRequests();

        operationTracer.traceStartBlock(new MockWorldView(), new MockProcessableBlockHeader(1L), null);

        // TX1 - success
        {
            var evaluationContext = new MockTransactionEvaluationContext("0x1");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);
            var postResult = simulatePostProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);
        }

        // TX2 - success
        {
            var evaluationContext = new MockTransactionEvaluationContext("0x2a");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);
            var postResult = simulatePostProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);
        }

        // TX3 - first 
        {
            var evaluationContext = new MockTransactionEvaluationContext("0x2b");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);
            var postResult = simulatePostProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);
        }

        // TX4 - first 
        {
            var evaluationContext = new MockTransactionEvaluationContext("0x2c");
            var preResult = simulatePreProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, preResult);
            var postResult = simulatePostProcessing(selector, evaluationContext);
            assertEquals(TransactionSelectionResult.SELECTED, postResult);
        }

        TransactionSelectionResult rollbackResult = mock(TransactionSelectionResult.class);
        when(rollbackResult.toString()).thenReturn("SELECTED_ROLLBACK");
        selector.onTransactionNotSelected(new MockTransactionEvaluationContext("0x2a"), rollbackResult);
        selector.onTransactionNotSelected(new MockTransactionEvaluationContext("0x2b"), rollbackResult);
        selector.onTransactionNotSelected(new MockTransactionEvaluationContext("0x2c"), TransactionSelectionResult.invalid("tx rejected"));
        

        // Wait for async reorg request to be sent
        Thread.sleep(100);

        // Verify reorg was sent
        var reorgRequests = mockTransport.getReorgRequests();
        assertEquals(1, reorgRequests.size());

        operationTracer.traceEndBlock(new MockBlockHeader(1L), new MockBlockBody(1));
    }
}
