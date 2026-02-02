package net.phylax.credible.strategy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.google.common.base.Stopwatch;

import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.transport.MockTransport;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.CredibleRejectionReason;
import net.phylax.credible.types.SidecarApiModels.*;
import net.phylax.credible.utils.Result;
import net.phylax.credible.metrics.SimpleMockMetricsSystem;

public class DefaultStrategyTest {

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

    NewIteration generateNewIteration() {
        // generate block env
        BlockEnv blockEnvData = new BlockEnv(
            1L,
            hexToBytes("0x0000000000000000000000000000000000000001"),
            System.currentTimeMillis(),
            200000L,
            10L,
            hexToBytes("0x123"),
            hexToBytes("0x123123123123123123123123123123"),
            new BlobExcessGasAndPrice(1L, 1L)
        );
        byte[] parentBlockHash = hexToBytes("0x0000000000000000000000000000000000000002");
        byte[] parentBeaconBlockRoot = hexToBytes("0x0000000000000000000000000000000000000003");
        return new NewIteration(1L, blockEnvData, parentBlockHash, parentBeaconBlockRoot);
    }

    CommitHead generateNewCommitHead() {
        byte[] lastTxHash = hexToBytes("0x0000000000000000000000000000000000000001");
        byte[] blockHash = hexToBytes("0x0000000000000000000000000000000000000002");
        byte[] parentBeaconBlockRoot = hexToBytes("0x0000000000000000000000000000000000000003");
        return new CommitHead(lastTxHash, 1, 1L, 1L, blockHash, parentBeaconBlockRoot, System.currentTimeMillis() / 1000);
    }

    SendTransactionsRequest generateTransactionRequest(byte[] hash) {
        // generate transaction request
        var transactions = new ArrayList<TransactionExecutionPayload>();
        TxExecutionId txExecutionId = new TxExecutionId(0L, 1L, hash, 0L);
        transactions.add(new TransactionExecutionPayload(txExecutionId, new TxEnv(), hexToBytes("0x1234567890")));
        return new SendTransactionsRequest(transactions);
    }

    ISidecarStrategy initStrategy(
        List<ISidecarTransport> primaryTransports,
        List<ISidecarTransport> fallbackTransports,
        int processingTimeout,
        boolean shouldSendBlockEnv
    ) {
        var metricsSystem = new SimpleMockMetricsSystem();
        var metrics = new CredibleMetricsRegistry(metricsSystem);
        
        var strategy =  new DefaultSidecarStrategy(
            primaryTransports == null ? new ArrayList<>() : primaryTransports,
            fallbackTransports == null ? new ArrayList<>() : fallbackTransports,
            processingTimeout,
            metrics);

        var newIteration = generateNewIteration();
        var commitHead = generateNewCommitHead();
        assertDoesNotThrow(() -> strategy.commitHead(commitHead, 1000));
        assertDoesNotThrow(() -> strategy.newIteration(newIteration).join());

        return strategy;
    }

    ISidecarStrategy initStrategy(
        ISidecarTransport primaryTransport,
        ISidecarTransport fallbackTransport,
        int processingTimeout,
        boolean shouldSendBlockEnv
    ) {
        return initStrategy(
            primaryTransport == null ? new ArrayList<>() : Arrays.asList(primaryTransport),
            fallbackTransport == null ? new ArrayList<>() : Arrays.asList(fallbackTransport),
            processingTimeout,
            shouldSendBlockEnv);
    }

    /**
     * Calls sendTransactions and getTransactions and waiting for the polling result
     * @param strategy Strategy
     * @return GetTransactionsResponse
     */
    Result<GetTransactionResponse, CredibleRejectionReason> sendTransaction(ISidecarStrategy strategy) {
        byte[] hash = hexToBytes("0x1" + new Random().nextInt(Integer.MAX_VALUE));

        strategy.dispatchTransactions(generateTransactionRequest(hash));
        GetTransactionRequest txRequest = new GetTransactionRequest(0L, 1L, hash, 0);
        var response = strategy.getTransactionResult(txRequest);
        return response;
    }

    @Test
    void shouldProcessTransactions() {
        var mockTransport = new MockTransport(200);
        var strategy = initStrategy(mockTransport, null, 500, true);

        byte[] hash1 = hexToBytes("0x1");
        assertDoesNotThrow(() -> strategy.dispatchTransactions(generateTransactionRequest(hash1)));
        GetTransactionRequest txReq1 = new GetTransactionRequest(0L, 1L, hash1, 0);
        var response = strategy.getTransactionResult(txReq1);
        assertNotNull(response.getSuccess().getResult());

        byte[] hash2 = hexToBytes("0x2");
        assertDoesNotThrow(() -> strategy.dispatchTransactions(generateTransactionRequest(hash2)));
        GetTransactionRequest txReq2 = new GetTransactionRequest(0L, 1L, hash2, 0);
        response = strategy.getTransactionResult(txReq2);
        assertNotNull(response.getSuccess().getResult());

        byte[] hash3 = hexToBytes("0x3");
        assertDoesNotThrow(() -> strategy.dispatchTransactions(generateTransactionRequest(hash3)));
        GetTransactionRequest txReq3 = new GetTransactionRequest(0L, 1L, hash3, 0);
        response = strategy.getTransactionResult(txReq3);
        assertNotNull(response.getSuccess().getResult());
    }

    @Test
    void shouldNotProcessDueToTimeout() {
        var mockTransport = new MockTransport(800);
        var mockTransportFallback = new MockTransport(800);
        var strategy = initStrategy(mockTransport, mockTransportFallback, 500, true);

        var response = sendTransaction(strategy);
        assertEquals(response.getFailure(), CredibleRejectionReason.PROCESSING_TIMEOUT);
    }

    @Test
    void shouldProcessFromSidecarThatDoesntTimeout() {
        var mockTransport = new MockTransport(800);
        var mockTransport2 = new MockTransport(200);
        var strategy = initStrategy(Arrays.asList(mockTransport, mockTransport2), null, 500, true);

        var response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
    }

    @Test
    void shouldProcessFromSecondSidecar() {
        // First transport throws on sendTransactions
        var mockTransport = new MockTransport(200);
        mockTransport.setThrowOnSendTx(true);

        var mockTransport2 = new MockTransport(500);
        var strategy = initStrategy(Arrays.asList(mockTransport, mockTransport2), null, 800, false);

        strategy.newIteration(generateNewIteration());
        var response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());

        // First transport throws on getTransactions
        // NOTE: even though the first sidecar is faster, the result of the second one will be used
        mockTransport.setThrowOnSendTx(false);
        mockTransport.setThrowOnGetTx(true);

        strategy.newIteration(generateNewIteration());
        response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
    }

    @Test
    void shouldProcessFromFasterSidecar() {
        int longProcessingTime = 1000;
        int fastProcessingTime = 400;
        int processingTimeout = 3000;
        
        var mockTransport = new MockTransport(longProcessingTime);
        var mockTransport2 = new MockTransport(fastProcessingTime);

        // Working fallback
        var mockTransportFallback = new MockTransport(longProcessingTime);
        var strategy = initStrategy(
            Arrays.asList(mockTransport, mockTransport2),
            Arrays.asList(mockTransportFallback),
            processingTimeout,
            false
        );

        strategy.newIteration(generateNewIteration()).join();

        Stopwatch stopwatch = Stopwatch.createStarted();
        var response = sendTransaction(strategy);
        stopwatch.stop();
        assertNotNull(response.getSuccess().getResult());

        // The time elapsed shouldn't be much more than the fastest one
        long elapsed = stopwatch.elapsed().toMillis();
        assertTrue(elapsed < longProcessingTime);
        assertTrue(elapsed >= fastProcessingTime);
    }

    @Test
    void shouldCheckIfActiveAfterAllTransportsTimeout() {
        // Primary sidecars throw on sendBlockEnv
        var mockTransport = new MockTransport(100);
        var mockTransport2 = new MockTransport(100);
        var mockTransport3 = new MockTransport(100);

        // Working fallback
        var mockTransportFallback = new MockTransport(100);
        var strategy = initStrategy(
            Arrays.asList(mockTransport, mockTransport2, mockTransport3),
            Arrays.asList(mockTransportFallback),
            300,
            false
        );

        strategy.newIteration(generateNewIteration()).join();

        var response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
        assertEquals(strategy.isActive(), true);

        // First two sidecars times out
        mockTransport.setProcessingLatency(500);
        mockTransport2.setProcessingLatency(500);

        response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
        assertEquals(strategy.isActive(), true);

        // Last sidecar times out
        mockTransport3.setProcessingLatency(500);

        response = sendTransaction(strategy);
        assertTrue(response.getFailure() == CredibleRejectionReason.PROCESSING_TIMEOUT);
        assertEquals(strategy.isActive(), false);

        // Should still be inactive
        response = sendTransaction(strategy);
        assertFalse(response.isSuccess());
        assertTrue(response.getFailure() == CredibleRejectionReason.NO_ACTIVE_TRANSPORT);

        // One sidecar gets up again
        mockTransport3.setProcessingLatency(100);

        // Still inactive until next block
        response = sendTransaction(strategy);
        assertFalse(response.isSuccess());
        assertTrue(response.getFailure() == CredibleRejectionReason.NO_ACTIVE_TRANSPORT);

        // All transport get back up
        mockTransport.setProcessingLatency(100);
        mockTransport2.setProcessingLatency(100);

        // Still inactive until next block
        response = sendTransaction(strategy);
        assertFalse(response.isSuccess());
        assertTrue(response.getFailure() == CredibleRejectionReason.NO_ACTIVE_TRANSPORT);

        // New block, activate again
        strategy.commitHead(generateNewCommitHead(), 1000);
        strategy.newIteration(generateNewIteration()).join();

        response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
        assertEquals(strategy.isActive(), true);
    }

    @Test
    void shouldProcessOnSlowBlockEnv() {
        // First transport throws on sendTransactions
        var mockTransport = new MockTransport(200);
        mockTransport.setSendEventsLatency(1000);

        var strategy = initStrategy(Arrays.asList(mockTransport), null, 800, false);

        strategy.newIteration(generateNewIteration());
        // It should return an empty list (same as when transports aren't active)
        var response = strategy.dispatchTransactions(generateTransactionRequest(hexToBytes("0x1")));
        assertTrue(response.size() == 1);

        // GetTransactions should reject
        GetTransactionRequest txReq = new GetTransactionRequest(0L, 1L, hexToBytes("0x1"), 0);
        var result = strategy.getTransactionResult(txReq);
        assertNotNull(result.getSuccess().getResult());
    }

    @Test
    void shouldProcessFromSidecarThatDoesntTimeoutOnSendBlockEnv() {
        // First transport throws on sendTransactions
        var mockTransport = new MockTransport(200);

        var mockTransport2 = new MockTransport(200);
        mockTransport.setSendEventsLatency(1000);

        var strategy = initStrategy(Arrays.asList(mockTransport, mockTransport2), null, 800, false);

        strategy.newIteration(generateNewIteration());
        var response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
    }

    @Test
    void shouldFallbackToGetTransactionWhenStreamTimesOut() {
        int processingTimeout = 1000;
        
        // processingLatency controls stream result delay - set to exceed stream timeout
        int streamResultLatency = 900;
        // getTransaction fallback responds quickly within fallback timeout
        int fallbackLatency = 100;

        var mockTransport = new MockTransport(streamResultLatency);
        mockTransport.setGetTransactionLatency(fallbackLatency);

        var strategy = initStrategy(mockTransport, null, processingTimeout, true);

        Stopwatch stopwatch = Stopwatch.createStarted();
        var response = sendTransaction(strategy);
        stopwatch.stop();

        // Should succeed via fallback
        assertNotNull(response.getSuccess());
        assertNotNull(response.getSuccess().getResult());

        long elapsed = stopwatch.elapsed().toMillis();
        assertTrue("Total time should be less than processingTimeout, was: " + elapsed,
            elapsed < processingTimeout);
        assertTrue("Should have waited for stream timeout (800ms), was: " + elapsed,
            elapsed >= 800);
    }
}
