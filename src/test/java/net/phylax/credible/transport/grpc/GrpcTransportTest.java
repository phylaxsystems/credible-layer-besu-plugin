package net.phylax.credible.transport.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.metrics.SimpleMockMetricsSystem;
import net.phylax.credible.types.SidecarApiModels;
import sidecar.transport.v1.Sidecar;
import sidecar.transport.v1.SidecarTransportGrpc;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GrpcTransport using an in-process gRPC server
 */
public class GrpcTransportTest {

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

    // Helper to convert long to 32-byte array (big-endian, left-padded with zeros)
    private static byte[] longToBytes32(long value) {
        byte[] bytes = new byte[32];
        for (int i = 31; i >= 24; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }

    private static ByteString longToByteString(long value) {
        byte[] bytes = new byte[32];
        for (int i = 31; i >= 24; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return ByteString.copyFrom(bytes);
    }

    private static long byteStringToLong(ByteString byteString) {
        if (byteString == null || byteString.isEmpty()) {
            return 0L;
        }
        byte[] bytes = byteString.toByteArray();
        long result = 0;
        int start = Math.max(0, bytes.length - 8);
        for (int i = start; i < bytes.length; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }
    private GrpcTransport transport;
    private TestSidecarService testService;
    private final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    /**
     * Test implementation of the SidecarTransport service
     */
    private static class TestSidecarService extends SidecarTransportGrpc.SidecarTransportImplBase {
        // Store last received requests for verification
        public final AtomicReference<Sidecar.Event> lastEvent = new AtomicReference<>();
        public final List<Sidecar.Event> receivedEvents = new ArrayList<>();
        public Sidecar.GetTransactionsRequest lastGetTransactionsRequest;
        public Sidecar.GetTransactionRequest lastGetTransactionRequest;

        // Configurable responses
        public boolean streamAckSuccess = true;
        public String streamAckMessage = "Events processed";
        public List<Sidecar.TransactionResult> transactionResults = new ArrayList<>();
        public List<ByteString> notFoundTxHashes = new ArrayList<>();

        @Override
        public StreamObserver<Sidecar.Event> streamEvents(StreamObserver<Sidecar.StreamAck> responseObserver) {
            return new StreamObserver<>() {
                private long eventsProcessed = 0;

                @Override
                public void onNext(Sidecar.Event event) {
                    lastEvent.set(event);
                    receivedEvents.add(event);
                    eventsProcessed++;

                    // Send ack for each event
                    responseObserver.onNext(Sidecar.StreamAck.newBuilder()
                        .setSuccess(streamAckSuccess)
                        .setMessage(streamAckMessage)
                        .setEventsProcessed(eventsProcessed)
                        .build());
                }

                @Override
                public void onError(Throwable t) {
                    responseObserver.onError(t);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void getTransactions(Sidecar.GetTransactionsRequest request,
                                   StreamObserver<Sidecar.GetTransactionsResponse> responseObserver) {
            lastGetTransactionsRequest = request;
            responseObserver.onNext(Sidecar.GetTransactionsResponse.newBuilder()
                .addAllResults(transactionResults)
                .addAllNotFound(notFoundTxHashes)
                .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getTransaction(Sidecar.GetTransactionRequest request,
                                   StreamObserver<Sidecar.GetTransactionResponse> responseObserver) {
            lastGetTransactionRequest = request;
            responseObserver.onNext(Sidecar.GetTransactionResponse.newBuilder()
                .setResult(transactionResults.get(0))
                .build());
            responseObserver.onCompleted();
        }

        // Store the results observer for sending results later
        public StreamObserver<Sidecar.TransactionResult> resultsObserver;

        @Override
        public void subscribeResults(Sidecar.SubscribeResultsRequest request,
                                    StreamObserver<Sidecar.TransactionResult> responseObserver) {
            this.resultsObserver = responseObserver;
            // Keep the stream open - results will be sent via sendResult()
        }

        /**
         * Send a result to the subscribed client
         */
        public void sendResult(Sidecar.TransactionResult result) {
            if (resultsObserver != null) {
                resultsObserver.onNext(result);
            }
        }

        public void reset() {
            lastEvent.set(null);
            receivedEvents.clear();
            lastGetTransactionsRequest = null;
            lastGetTransactionRequest = null;
            transactionResults.clear();
            notFoundTxHashes.clear();
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        testService = new TestSidecarService();

        // Generate a unique in-process server name
        String serverName = InProcessServerBuilder.generateName();

        // Create and start an in-process server
        grpcCleanup.register(InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(testService)
            .build()
            .start());

        // Create an in-process channel
        ManagedChannel channel = grpcCleanup.register(
            InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build());

        var metrics = new CredibleMetricsRegistry(new SimpleMockMetricsSystem());

        // Create the transport with the in-process channel
        transport = new GrpcTransport(channel, channel, 5000, metrics);
    }

    @AfterEach
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    public void testSendEventsWithCommitHead() throws Exception {
        // Create a CommitHead event
        SidecarApiModels.CommitHead commitHead = new SidecarApiModels.CommitHead(
            hexToBytes("0xabcdef1234567890"),  // lastTxHash
            10,                     // nTransactions
            12345L,                 // blockNumber
            1L                      // selectedIterationId
        );

        SidecarApiModels.CommitHeadReqItem commitHeadItem =
            new SidecarApiModels.CommitHeadReqItem(commitHead);

        List<SidecarApiModels.SendEventsRequestItem> events = new ArrayList<>();
        events.add(commitHeadItem);

        SidecarApiModels.SendEventsRequest request = new SidecarApiModels.SendEventsRequest(events);

        // Send the request
        CompletableFuture<SidecarApiModels.SendEventsResponse> future = transport.sendEvents(request);
        SidecarApiModels.SendEventsResponse response = future.get();

        // Verify the response
        assertEquals("accepted", response.getStatus());

        // Give some time for the stream to process
        Thread.sleep(100);

        // Verify the request was received correctly
        assertFalse(testService.receivedEvents.isEmpty());

        Sidecar.Event event = testService.receivedEvents.get(0);
        assertTrue(event.hasCommitHead());

        Sidecar.CommitHead receivedCommitHead = event.getCommitHead();
        assertEquals(10, receivedCommitHead.getNTransactions());
        assertEquals(12345L, byteStringToLong(receivedCommitHead.getBlockNumber()));
        assertEquals(1L, receivedCommitHead.getSelectedIterationId());
    }

    @Test
    public void testSendTransactions() throws Exception {
        // Create test transactions
        List<SidecarApiModels.TransactionExecutionPayload> transactions = new ArrayList<>();

        SidecarApiModels.TxEnv txEnv = new SidecarApiModels.TxEnv();
        txEnv.setCaller(hexToBytes("0xabcdef1234567890abcdef1234567890abcdef12"));
        txEnv.setGasLimit(21000L);
        txEnv.setGasPrice(longToBytes32(1_000_000_000L));
        txEnv.setKind(hexToBytes("0xabcdef1234567890abcdef1234567890abcdef34"));
        txEnv.setValue(hexToBytes("0x1000000000000000000"));
        txEnv.setData(hexToBytes("0x"));
        txEnv.setNonce(0L);
        txEnv.setChainId(1L);
        txEnv.setTxType((byte) 2);
        txEnv.setMaxFeePerBlobGas(longToBytes32(25L));
        txEnv.setGasPriorityFee(longToBytes32(2L));
        txEnv.setBlobHashes(List.of(hexToBytes("0xabcdef1234567890")));

        SidecarApiModels.AccessListEntry accessListEntry = new SidecarApiModels.AccessListEntry(
            hexToBytes("0xabcdef1234567890abcdef1234567890abcdef56"), List.of(hexToBytes("0xaabb"), hexToBytes("0xccdd")));
        txEnv.setAccessList(List.of(accessListEntry));

        SidecarApiModels.AuthorizationListEntry authorizationListEntry = new SidecarApiModels.AuthorizationListEntry();
        authorizationListEntry.setAddress(hexToBytes("0xabcdef1234567890abcdef1234567890abcdef78"));
        authorizationListEntry.setV((byte) 1);
        authorizationListEntry.setR(hexToBytes("0xaabbccdd"));
        authorizationListEntry.setS(hexToBytes("0xeeff0011"));
        authorizationListEntry.setChainId(1L);
        authorizationListEntry.setNonce(5L);
        txEnv.setAuthorizationList(List.of(authorizationListEntry));

        SidecarApiModels.TxExecutionId txExecutionId = new SidecarApiModels.TxExecutionId(
            12345L, 1L, hexToBytes("0xaabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"), 0L
        );
        transactions.add(new SidecarApiModels.TransactionExecutionPayload(
            txExecutionId, txEnv, hexToBytes("0x1234567890abcdef")
        ));

        SidecarApiModels.SendTransactionsRequest request =
            new SidecarApiModels.SendTransactionsRequest(transactions);

        // Send the request
        CompletableFuture<SidecarApiModels.SendTransactionsResponse> future =
            transport.sendTransactions(request);
        SidecarApiModels.SendTransactionsResponse response = future.get();

        // Verify the response
        assertEquals("success", response.getStatus());
        assertEquals(1, response.getRequestCount());

        // Give some time for the stream to process
        Thread.sleep(100);

        // Verify the request was received correctly via stream
        assertFalse(testService.receivedEvents.isEmpty());
        Sidecar.Event event = testService.receivedEvents.get(0);
        assertTrue(event.hasTransaction());

        Sidecar.TransactionEnv protoEnv = event.getTransaction().getTxEnv();
        assertEquals(2, protoEnv.getTxType());
    }

    @Test
    public void testGetTransactions() throws Exception {
        // Configure test service with some results
        testService.transactionResults.add(
            Sidecar.TransactionResult.newBuilder()
                .setTxExecutionId(Sidecar.TxExecutionId.newBuilder()
                    .setBlockNumber(longToByteString(0L))
                    .setIterationId(0L)
                    .setTxHash(hexToByteString("0xaabbccdd11223344"))
                    .build())
                .setStatus(Sidecar.ResultStatus.RESULT_STATUS_SUCCESS)
                .setGasUsed(21000)
                .setError("")
                .build()
        );
        testService.notFoundTxHashes.add(hexToByteString("0xeeff00112233"));

        // Create request with TxExecutionId
        SidecarApiModels.GetTransactionsRequest txReq = new SidecarApiModels.GetTransactionsRequest();
        txReq.setTxExecutionIds(List.of(
            new SidecarApiModels.TxExecutionId(1000L, 1L, hexToBytes("0xaabbccdd11223344"), 0L),
            new SidecarApiModels.TxExecutionId(1000L, 1L, hexToBytes("0xeeff00112233"), 1L)
        ));

        // Send the request
        CompletableFuture<SidecarApiModels.GetTransactionsResponse> future =
            transport.getTransactions(txReq);
        SidecarApiModels.GetTransactionsResponse response = future.get();

        // Verify the response
        assertEquals(1, response.getResults().size());
        assertEquals(1, response.getNotFound().size());

        SidecarApiModels.TransactionResult result = response.getResults().get(0);
        assertEquals("success", result.getStatus());
        assertEquals(21000, result.getGasUsed());

        // Verify the request was received correctly
        assertNotNull(testService.lastGetTransactionsRequest);
        assertEquals(2, testService.lastGetTransactionsRequest.getTxExecutionIdsCount());
    }

    @Test
    public void testGetTransaction() throws Exception {
        // Configure test service with some results
        testService.transactionResults.add(
            Sidecar.TransactionResult.newBuilder()
                .setTxExecutionId(Sidecar.TxExecutionId.newBuilder()
                    .setBlockNumber(longToByteString(0L))
                    .setIterationId(0L)
                    .setTxHash(hexToByteString("0xaabbccdd11223344"))
                    .build())
                .setStatus(Sidecar.ResultStatus.RESULT_STATUS_SUCCESS)
                .setGasUsed(21000)
                .setError("")
                .build()
        );

        // Create request with TxExecutionId
        SidecarApiModels.GetTransactionRequest txReq = new SidecarApiModels.GetTransactionRequest(1000L, 1L, hexToBytes("0xaabbccdd11223344"), 0);

        // Send the request
        CompletableFuture<SidecarApiModels.GetTransactionResponse> future =
            transport.getTransaction(txReq);
        SidecarApiModels.GetTransactionResponse response = future.get();

        // Verify the response
        assertTrue(response.getResult() != null);

        SidecarApiModels.TransactionResult result = response.getResult();
        assertEquals("success", result.getStatus());
        assertEquals(21000, result.getGasUsed());

        // Verify the request was received correctly
        assertNotNull(testService.lastGetTransactionRequest);
    }

    @Test
    public void testSendReorg() throws Exception {
        // Create reorg request with TxExecutionId
        SidecarApiModels.ReorgRequest request =
            new SidecarApiModels.ReorgRequest(12345L, 1L, hexToBytes("0xaabbccdd112233445566778899aabbccddeeff00"), 0);

        // Send the request
        CompletableFuture<SidecarApiModels.ReorgResponse> future = transport.sendReorg(request);
        SidecarApiModels.ReorgResponse response = future.get();

        // Verify the response
        assertTrue(response.getSuccess());
        assertNull(response.getError());

        // Give some time for the stream to process
        Thread.sleep(100);

        // Verify the reorg event was sent via stream
        assertFalse(testService.receivedEvents.isEmpty());
        Sidecar.Event event = testService.receivedEvents.get(0);
        assertTrue(event.hasReorg());
        assertEquals(12345L, byteStringToLong(event.getReorg().getTxExecutionId().getBlockNumber()));
        assertEquals(1L, event.getReorg().getTxExecutionId().getIterationId());
    }

    @Test
    public void testModelConversionsAreReversible() throws Exception {
        // Test that converting POJO → Proto → POJO maintains data integrity for SendEvents
        SidecarApiModels.CommitHead commitHead = new SidecarApiModels.CommitHead(
            hexToBytes("0xaabbccdd11223344"), 5, 99999L, 1L
        );

        SidecarApiModels.CommitHeadReqItem commitHeadItem =
            new SidecarApiModels.CommitHeadReqItem(commitHead);

        List<SidecarApiModels.SendEventsRequestItem> events = new ArrayList<>();
        events.add(commitHeadItem);

        SidecarApiModels.SendEventsRequest originalRequest = new SidecarApiModels.SendEventsRequest(events);

        // Send through transport and verify round-trip works
        testService.streamAckSuccess = true;
        CompletableFuture<SidecarApiModels.SendEventsResponse> future =
            transport.sendEvents(originalRequest);
        SidecarApiModels.SendEventsResponse response = future.get();

        assertEquals("accepted", response.getStatus());

        // Give some time for the stream to process
        Thread.sleep(100);

        assertFalse(testService.receivedEvents.isEmpty());

        Sidecar.Event receivedEvent = testService.receivedEvents.get(0);
        assertTrue(receivedEvent.hasCommitHead());
        assertEquals(commitHead.getNTransactions().intValue(), receivedEvent.getCommitHead().getNTransactions());
        assertEquals(commitHead.getBlockNumber().longValue(), byteStringToLong(receivedEvent.getCommitHead().getBlockNumber()));
    }

    @Test
    public void testSubscribeResults() throws Exception {
        // Set up a latch to wait for results
        CountDownLatch resultLatch = new CountDownLatch(1);
        AtomicReference<SidecarApiModels.TransactionResult> receivedResult = new AtomicReference<>();
        AtomicReference<Throwable> receivedError = new AtomicReference<>();

        // Subscribe to results
        transport.subscribeResults(
            result -> {
                receivedResult.set(result);
                resultLatch.countDown();
            },
            error -> {
                receivedError.set(error);
                resultLatch.countDown();
            }
        ).get();

        // Give time for the subscription to be established
        Thread.sleep(100);

        // Send a result from the server
        Sidecar.TransactionResult protoResult = Sidecar.TransactionResult.newBuilder()
            .setTxExecutionId(Sidecar.TxExecutionId.newBuilder()
                .setBlockNumber(longToByteString(1000L))
                .setIterationId(1L)
                .setTxHash(hexToByteString("0xaabbccdd11223344556677889900aabbccdd11223344556677889900aabbccdd"))
                .setIndex(0)
                .build())
            .setStatus(Sidecar.ResultStatus.RESULT_STATUS_SUCCESS)
            .setGasUsed(21000)
            .setError("")
            .build();

        testService.sendResult(protoResult);

        // Wait for the result
        assertTrue(resultLatch.await(5, TimeUnit.SECONDS), "Should receive result within timeout");

        // Verify the result
        assertNull(receivedError.get(), "Should not have received an error");
        assertNotNull(receivedResult.get(), "Should have received a result");

        SidecarApiModels.TransactionResult result = receivedResult.get();
        assertEquals("success", result.getStatus());
        assertEquals(21000, result.getGasUsed());
        assertEquals(1000L, result.getTxExecutionId().getBlockNumber());
    }

    /**
     * Helper method to convert hex string to ByteString
     */
    private static ByteString hexToByteString(String hex) {
        if (hex == null || hex.isEmpty()) {
            return ByteString.EMPTY;
        }
        String cleanHex = hex.startsWith("0x") || hex.startsWith("0X")
            ? hex.substring(2)
            : hex;

        if (cleanHex.isEmpty()) {
            return ByteString.EMPTY;
        }

        if (cleanHex.length() % 2 != 0) {
            cleanHex = "0" + cleanHex;
        }

        byte[] bytes = new byte[cleanHex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;
            bytes[i] = (byte) Integer.parseInt(cleanHex.substring(index, index + 2), 16);
        }
        return ByteString.copyFrom(bytes);
    }
}
