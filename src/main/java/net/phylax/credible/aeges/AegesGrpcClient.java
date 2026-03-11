package net.phylax.credible.aeges;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.transport.grpc.BaseGrpcTransport;

import aeges.v1.Aeges;
import aeges.v1.AegesServiceGrpc;


/**
 * gRPC client for the Aeges service.
 */
@Slf4j
public class AegesGrpcClient extends BaseGrpcTransport {
    private final AegesServiceGrpc.AegesServiceStub stub;

    // Stream management
    private final AtomicReference<StreamObserver<Aeges.VerifyTransactionRequest>> requestStreamRef = new AtomicReference<>();
    private final Object streamLock = new Object();
    private volatile boolean connected = false;

    private final LinkedBlockingQueue<CompletableFuture<Aeges.VerifyTransactionResponse>> pendingResponses = new LinkedBlockingQueue<>();

    public AegesGrpcClient(ManagedChannel channel, long deadlineMillis) {
        super(channel, deadlineMillis);
        this.stub = AegesServiceGrpc.newStub(channel);
    }

    /**
     * Open (or reopen) the VerifyTransaction stream.
     */
    public void connect() {
        synchronized (streamLock) {
            if (connected && requestStreamRef.get() != null) {
                return;
            }

            resetChannelBackoff();
            ConnectivityState state = getChannelState(true);
            log.info("Opening Aeges VerifyTransaction stream, channel state: {}", state);

            if (state == ConnectivityState.TRANSIENT_FAILURE) {
                log.warn("Aeges channel in TRANSIENT_FAILURE state, will retry on next call");
                return;
            }

            StreamObserver<Aeges.VerifyTransactionResponse> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(Aeges.VerifyTransactionResponse response) {
                    CompletableFuture<Aeges.VerifyTransactionResponse> future = pendingResponses.poll();
                    if (future != null) {
                        future.complete(response);
                    } else {
                        log.warn("Received Aeges response but no pending future in queue");
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Aeges VerifyTransaction stream error: {}", getErrorMessage(t), t);
                    tearDown(t);
                }

                @Override
                public void onCompleted() {
                    log.info("Aeges VerifyTransaction stream completed by server");
                    tearDown(new RuntimeException("Stream completed by server"));
                }
            };

            StreamObserver<Aeges.VerifyTransactionRequest> requestObserver = stub.verifyTransaction(responseObserver);
            requestStreamRef.set(requestObserver);
            connected = true;
        }
    }

    /**
     * Send a transaction for verification.
     *
     * @return the response, or null if the service is unavailable or times out
     */
    public Aeges.VerifyTransactionResponse verifyTransaction(Aeges.Transaction protoTx) {
        // Lazy reconnect
        if (!connected || requestStreamRef.get() == null) {
            connect();
        }

        StreamObserver<Aeges.VerifyTransactionRequest> stream = requestStreamRef.get();
        if (stream == null) {
            log.debug("Aeges stream not available");
            return null;
        }

        CompletableFuture<Aeges.VerifyTransactionResponse> future = new CompletableFuture<>();
        pendingResponses.add(future);

        Aeges.VerifyTransactionRequest request = Aeges.VerifyTransactionRequest.newBuilder()
            .setTransaction(protoTx)
            .build();

        synchronized (streamLock) {
            try {
                stream.onNext(request);
            } catch (Exception e) {
                pendingResponses.remove(future);
                log.error("Error sending to Aeges stream: {}", e.getMessage(), e);
                tearDown(e);
                return null;
            }
        }

        try {
            return future.get(deadlineMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingResponses.remove(future);
            log.warn("Aeges verification timed out after {}ms, reconnecting stream.", deadlineMillis);
            tearDown(e);
            return null;
        } catch (Exception e) {
            pendingResponses.remove(future);
            log.error("Error awaiting Aeges response: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Tear down the stream and fail all pending futures.
     */
    private void tearDown(Throwable cause) {
        connected = false;
        requestStreamRef.set(null);
        CompletableFuture<Aeges.VerifyTransactionResponse> pending;
        while ((pending = pendingResponses.poll()) != null) {
            pending.completeExceptionally(cause);
        }
    }

    /**
     * Close the stream and shut down the channel.
     */
    public void close() {
        synchronized (streamLock) {
            StreamObserver<Aeges.VerifyTransactionRequest> stream = requestStreamRef.getAndSet(null);
            if (stream != null) {
                try {
                    stream.onCompleted();
                } catch (Exception e) {
                    log.warn("Error closing Aeges stream: {}", e.getMessage());
                }
            }
            connected = false;
        }
        // Fail any remaining pending futures
        tearDown(new RuntimeException("Client closed"));
        shutdownChannel();
    }
}
