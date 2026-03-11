package net.phylax.credible.aeges;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;

import aeges.v1.Aeges;
import aeges.v1.AegesServiceGrpc;

import static org.junit.jupiter.api.Assertions.*;


public class AegesGrpcClientTest {

    private AegesGrpcClient client;
    private TestAegesService testService;
    private final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    /**
     * In-process Aeges service for testing.
     * By default, echoes back denied=false for each request.
     * Configurable via fields to simulate delays, errors, and denials.
     */
    private static class TestAegesService extends AegesServiceGrpc.AegesServiceImplBase {
        public final List<Aeges.VerifyTransactionRequest> receivedRequests = new ArrayList<>();
        public volatile boolean denyAll = false;
        public volatile long responseDelayMs = 0;
        public volatile boolean sendError = false;
        public volatile boolean completeImmediately = false;

        // Track the client-side stream observer so we can send errors/completions externally
        public final AtomicReference<StreamObserver<Aeges.VerifyTransactionResponse>> responseObserverRef = new AtomicReference<>();
        // Latch to signal when the stream is established
        public final CountDownLatch streamEstablished = new CountDownLatch(1);

        // Scheduler for delayed responses (avoids Thread.sleep on the calling thread)
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        @Override
        public StreamObserver<Aeges.VerifyTransactionRequest> verifyTransaction(
                StreamObserver<Aeges.VerifyTransactionResponse> responseObserver) {
            responseObserverRef.set(responseObserver);
            streamEstablished.countDown();

            if (completeImmediately) {
                responseObserver.onCompleted();
                return new NoOpRequestObserver<>();
            }

            return new StreamObserver<>() {
                @Override
                public void onNext(Aeges.VerifyTransactionRequest request) {
                    receivedRequests.add(request);

                    if (sendError) {
                        responseObserver.onError(
                            Status.INTERNAL.withDescription("test error").asRuntimeException());
                        return;
                    }

                    Runnable sendResponse = () -> {
                        try {
                            responseObserver.onNext(Aeges.VerifyTransactionResponse.newBuilder()
                                .setDenied(denyAll)
                                .build());
                        } catch (Exception e) {
                            // Stream may have been torn down by client timeout
                        }
                    };

                    if (responseDelayMs > 0) {
                        scheduler.schedule(sendResponse, responseDelayMs, TimeUnit.MILLISECONDS);
                    } else {
                        sendResponse.run();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    // Client closed with error
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        public void shutdown() {
            scheduler.shutdownNow();
        }

        public void reset() {
            receivedRequests.clear();
            denyAll = false;
            responseDelayMs = 0;
            sendError = false;
            completeImmediately = false;
        }
    }

    /** No-op request observer for cases where the server completes immediately. */
    private static class NoOpRequestObserver<T> implements StreamObserver<T> {
        @Override public void onNext(T value) {}
        @Override public void onError(Throwable t) {}
        @Override public void onCompleted() {}
    }

    @BeforeEach
    public void setUp() throws Exception {
        testService = new TestAegesService();

        String serverName = InProcessServerBuilder.generateName();

        grpcCleanup.register(InProcessServerBuilder
            .forName(serverName)
            .addService(testService)
            .build()
            .start());

        ManagedChannel channel = grpcCleanup.register(
            InProcessChannelBuilder.forName(serverName)
                .build());

        client = new AegesGrpcClient(channel, 2000);
        client.connect();
    }

    @AfterEach
    public void tearDown() {
        if (client != null) {
            client.close();
        }
        if (testService != null) {
            testService.shutdown();
        }
    }

    private static Aeges.Transaction makeProtoTx(String hashHex) {
        return Aeges.Transaction.newBuilder()
            .setHash(ByteString.copyFrom(hexToBytes(hashHex)))
            .setSender(ByteString.copyFrom(new byte[20]))
            .setValue(ByteString.copyFrom(new byte[32]))
            .setNonce(0)
            .setType(0)
            .setPayload(ByteString.EMPTY)
            .setGasLimit(21000)
            .build();
    }

    // --- Tests ---

    @Test
    public void transactionAllowed() {
        testService.denyAll = false;

        Aeges.VerifyTransactionResponse response = client.verifyTransaction(makeProtoTx("0x01"));

        assertNotNull(response);
        assertFalse(response.getDenied());
        assertEquals(1, testService.receivedRequests.size());
    }

    @Test
    public void transactionDenied() {
        testService.denyAll = true;

        Aeges.VerifyTransactionResponse response = client.verifyTransaction(makeProtoTx("0x02"));

        assertNotNull(response);
        assertTrue(response.getDenied());
    }

    @Test
    public void multipleTransactionsInOrder() {
        // Server will deny all — we verify each gets a response and requests arrive in order
        testService.denyAll = true;

        Aeges.VerifyTransactionResponse r1 = client.verifyTransaction(makeProtoTx("0xaa"));
        Aeges.VerifyTransactionResponse r2 = client.verifyTransaction(makeProtoTx("0xbb"));
        Aeges.VerifyTransactionResponse r3 = client.verifyTransaction(makeProtoTx("0xcc"));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);
        assertTrue(r1.getDenied());
        assertTrue(r2.getDenied());
        assertTrue(r3.getDenied());

        // Verify requests arrived in order
        assertEquals(3, testService.receivedRequests.size());
        assertEquals(
            ByteString.copyFrom(hexToBytes("0xaa")),
            testService.receivedRequests.get(0).getTransaction().getHash());
        assertEquals(
            ByteString.copyFrom(hexToBytes("0xbb")),
            testService.receivedRequests.get(1).getTransaction().getHash());
        assertEquals(
            ByteString.copyFrom(hexToBytes("0xcc")),
            testService.receivedRequests.get(2).getTransaction().getHash());
    }

    @Test
    public void timeoutReturnsNull() {
        // Server delays longer than client deadline
        testService.responseDelayMs = 5000;
        // Use a client with a very short deadline
        // We already have a 2s deadline, but let's make the delay exceed it
        // The default client has 2000ms deadline, 5000ms delay should trigger timeout

        Aeges.VerifyTransactionResponse response = client.verifyTransaction(makeProtoTx("0x03"));

        assertNull(response, "Should return null on timeout (fail-open)");
    }

    @Test
    public void reconnectsAfterStreamError() {
        // First call triggers server error
        testService.sendError = true;
        Aeges.VerifyTransactionResponse r1 = client.verifyTransaction(makeProtoTx("0x04"));
        assertNull(r1, "Should return null when server errors");

        // Reset server to normal
        testService.sendError = false;

        // Second call should reconnect and succeed
        Aeges.VerifyTransactionResponse r2 = client.verifyTransaction(makeProtoTx("0x05"));
        assertNotNull(r2, "Should succeed after reconnect");
        assertFalse(r2.getDenied());
    }

    @Test
    public void reconnectsAfterTimeout() {
        // First call times out (server delays)
        testService.responseDelayMs = 5000;
        Aeges.VerifyTransactionResponse r1 = client.verifyTransaction(makeProtoTx("0x06"));
        assertNull(r1, "Should return null on timeout");

        // Reset server to respond immediately
        testService.responseDelayMs = 0;

        // Next call should reconnect and succeed
        Aeges.VerifyTransactionResponse r2 = client.verifyTransaction(makeProtoTx("0x07"));
        assertNotNull(r2, "Should succeed after reconnect");
        assertFalse(r2.getDenied());
    }

    @Test
    public void serverCompletesStreamGracefully() throws Exception {
        // First, verify the stream works
        Aeges.VerifyTransactionResponse r1 = client.verifyTransaction(makeProtoTx("0x08"));
        assertNotNull(r1);

        // Server completes the stream
        assertTrue(testService.streamEstablished.await(2, TimeUnit.SECONDS));
        StreamObserver<Aeges.VerifyTransactionResponse> observer = testService.responseObserverRef.get();
        assertNotNull(observer);
        observer.onCompleted();

        // Give time for the onCompleted to propagate
        Thread.sleep(100);

        // Next call should reconnect and succeed
        Aeges.VerifyTransactionResponse r2 = client.verifyTransaction(makeProtoTx("0x09"));
        assertNotNull(r2, "Should succeed after server-initiated completion and reconnect");
    }

    @Test
    public void closeShutdownsCleanly() {
        // Verify the client works first
        Aeges.VerifyTransactionResponse r1 = client.verifyTransaction(makeProtoTx("0x0a"));
        assertNotNull(r1);

        // Close the client
        client.close();

        // After close, calls should return null (fail-open)
        Aeges.VerifyTransactionResponse r2 = client.verifyTransaction(makeProtoTx("0x0b"));
        assertNull(r2, "Should return null after close");

        // Prevent double-close in tearDown
        client = null;
    }

    @Test
    public void transactionFieldsPreservedInRequest() {
        testService.denyAll = false;

        Aeges.Transaction tx = Aeges.Transaction.newBuilder()
            .setHash(ByteString.copyFrom(hexToBytes("0xdeadbeef")))
            .setSender(ByteString.copyFrom(hexToBytes("0x1234567890abcdef1234567890abcdef12345678")))
            .setValue(ByteString.copyFrom(new byte[32]))
            .setNonce(42)
            .setType(2)
            .setPayload(ByteString.copyFrom(hexToBytes("0xaabbccdd")))
            .setGasLimit(100000)
            .setMaxFeePerGas(2000000000L)
            .setMaxPriorityFeePerGas(1000000000L)
            .build();

        client.verifyTransaction(tx);

        assertEquals(1, testService.receivedRequests.size());
        Aeges.Transaction received = testService.receivedRequests.get(0).getTransaction();
        assertEquals(42, received.getNonce());
        assertEquals(2, received.getType());
        assertEquals(100000, received.getGasLimit());
        assertEquals(2000000000L, received.getMaxFeePerGas());
        assertEquals(1000000000L, received.getMaxPriorityFeePerGas());
        assertEquals(ByteString.copyFrom(hexToBytes("0xaabbccdd")), received.getPayload());
    }

    // --- Helpers ---

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
}
