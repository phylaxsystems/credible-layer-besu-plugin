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

import io.opentelemetry.api.OpenTelemetry;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.transport.MockTransport;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.CredibleRejectionReason;
import net.phylax.credible.types.SidecarApiModels.*;
import net.phylax.credible.utils.Result;
import net.phylax.credible.metrics.SimpleMockMetricsSystem;
import net.phylax.credible.tracer.CredibleOperationTracer;

public class DefaultStrategyTest {
    SendBlockEnvRequest generateBlockEnv() {
        // generate block env
        BlockEnv blockEnvData = new BlockEnv(
            1L,
            "0x0000000000000000000000000000000000000001",
            System.currentTimeMillis(),
            200000L,
            10L,
            "0x123",
            "0x123123123123123123123123123123",
            new BlobExcessGasAndPrice(1L, 1L)
        );
        return new SendBlockEnvRequest(
            blockEnvData,
            "0x1110002220003330004000505060494959658484939485493845",
            1,
            null
        );
    }

    SendTransactionsRequest generateTransactionRequest(String hash) {
        // generate transaction request
        var transactions = new ArrayList<TransactionWithHash>();
        TxExecutionId txExecutionId = new TxExecutionId(0L, 1L, hash);
        transactions.add(new TransactionWithHash(txExecutionId, new TxEnv()));
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

        var openTelemetry = OpenTelemetry.noop();

        var operationTracer = new CredibleOperationTracer();
        
        var strategy =  new DefaultSidecarStrategy(
            primaryTransports == null ? new ArrayList<>() : primaryTransports,
            fallbackTransports == null ? new ArrayList<>() : fallbackTransports,
            operationTracer,
            processingTimeout,
            metrics,
            openTelemetry.getTracer("default-strategy"));

        var blockEnvRequest = generateBlockEnv();
        assertDoesNotThrow(() -> strategy.sendBlockEnv(blockEnvRequest).join());

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
        var hash = "0x1" + new Random().nextInt(Integer.MAX_VALUE);

        strategy.dispatchTransactions(generateTransactionRequest(hash));
        TxExecutionId txExecutionId = new TxExecutionId(0L, 1L, hash);
        var response = strategy.getTransactionResult(txExecutionId);
        return response;
    }

    @Test
    void shouldProcessTransactions() {
        var mockTransport = new MockTransport(200);
        var strategy = initStrategy(mockTransport, null, 500, true);

        String hash1 = "0x1";
        assertDoesNotThrow(() -> strategy.dispatchTransactions(generateTransactionRequest(hash1)));
        TxExecutionId txExecutionId1 = new TxExecutionId(0L, 1L, hash1);
        var response = strategy.getTransactionResult(txExecutionId1);
        assertNotNull(response.getSuccess().getResult());

        String hash2 = "0x2";
        assertDoesNotThrow(() -> strategy.dispatchTransactions(generateTransactionRequest(hash2)));
        TxExecutionId txExecutionId2 = new TxExecutionId(0L, 1L, hash2);
        response = strategy.getTransactionResult(txExecutionId2);
        assertNotNull(response.getSuccess().getResult());

        String hash3 = "0x3";
        assertDoesNotThrow(() -> strategy.dispatchTransactions(generateTransactionRequest(hash3)));
        TxExecutionId txExecutionId3 = new TxExecutionId(0L, 1L, hash3);
        response = strategy.getTransactionResult(txExecutionId3);
        assertNotNull(response.getSuccess().getResult());
    }

    @Test
    void shouldNotProcessDueToTimeout() {
        var mockTransport = new MockTransport(800);
        var mockTransportFallback = new MockTransport(800);
        var strategy = initStrategy(mockTransport, mockTransportFallback, 500, true);

        var response = sendTransaction(strategy);
        assertEquals(response.getFailure(), CredibleRejectionReason.TIMEOUT);
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

        strategy.sendBlockEnv(generateBlockEnv());
        var response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());

        // First transport throws on getTransactions
        // NOTE: even though the first sidecar is faster, the result of the second one will be used
        mockTransport.setThrowOnSendTx(false);
        mockTransport.setThrowOnGetTx(true);

        strategy.sendBlockEnv(generateBlockEnv());
        response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
    }

    @Test
    void shouldProcessFromFallback() {
        // Primary sidecars throw on sendBlockEnv
        var mockTransport = new MockTransport(100);
        mockTransport.setThrowOnSendBlockEnv(true);
        var mockTransport2 = new MockTransport(100);
        mockTransport2.setThrowOnSendBlockEnv(true);

        // Working fallback
        var mockTransportFallback = new MockTransport(200);
        var strategy = initStrategy(
            Arrays.asList(mockTransport, mockTransport2),
            Arrays.asList(mockTransportFallback),
            500,
            false
        );

        strategy.sendBlockEnv(generateBlockEnv()).join();

        var response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());

        // First sidecar gets back online
        mockTransport.setThrowOnSendBlockEnv(false);
        
        strategy.sendBlockEnv(generateBlockEnv()).join();
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

        strategy.sendBlockEnv(generateBlockEnv()).join();

        Stopwatch stopwatch = Stopwatch.createStarted();
        var response = sendTransaction(strategy);
        stopwatch.stop();
        assertNotNull(response.getSuccess().getResult());

        // The time elapsed shouldn't be much more than the fastest one
        long elapsed = stopwatch.elapsed().toMillis();
        assertTrue(elapsed < longProcessingTime);
        assertTrue(elapsed >= fastProcessingTime);

        // Set the processing latency on the first also to be faster
        mockTransport.setProcessingLatency(fastProcessingTime - 100);
        // Set a timeout on the sendTransactions for the first one
        mockTransport.setSendTransactionsLatency(fastProcessingTime + 100);

        strategy.sendBlockEnv(generateBlockEnv()).join();

        stopwatch = Stopwatch.createStarted();
        response = sendTransaction(strategy);
        stopwatch.stop();
        assertNotNull(response.getSuccess().getResult());

        // Even though the first sidecar is faster in processing, the result of the second one
        // will be used because the first one is slower on sendTransactions
        elapsed = stopwatch.elapsed().toMillis();
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

        strategy.sendBlockEnv(generateBlockEnv()).join();

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
        assertTrue(response.getFailure() == CredibleRejectionReason.TIMEOUT);
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
        strategy.sendBlockEnv(generateBlockEnv()).join();

        response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
        assertEquals(strategy.isActive(), true);
    }

    @Test
    void shouldProcessOnSlowBlockEnv() {
        // First transport throws on sendTransactions
        var mockTransport = new MockTransport(200);
        mockTransport.setSendBlockEnvLatency(1000);

        var strategy = initStrategy(Arrays.asList(mockTransport), null, 800, false);

        strategy.sendBlockEnv(generateBlockEnv());
        // It should return an empty list (same as when transports aren't active)
        var response = strategy.dispatchTransactions(generateTransactionRequest("0x1"));
        assertTrue(response.size() == 1);

        // GetTransactions should reject
        TxExecutionId txExecutionId = new TxExecutionId(0L, 1L, "0x1");
        var result = strategy.getTransactionResult(txExecutionId);
        assertNotNull(result.getSuccess().getResult());
    }

    @Test
    void shouldProcessFromSidecarThatDoesntTimeoutOnSendBlockEnv() {
        // First transport throws on sendTransactions
        var mockTransport = new MockTransport(200);

        var mockTransport2 = new MockTransport(200);
        mockTransport.setSendBlockEnvLatency(1000);

        var strategy = initStrategy(Arrays.asList(mockTransport, mockTransport2), null, 800, false);

        strategy.sendBlockEnv(generateBlockEnv());
        var response = sendTransaction(strategy);
        assertNotNull(response.getSuccess().getResult());
    }
}
