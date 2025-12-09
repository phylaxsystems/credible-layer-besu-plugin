package net.phylax.credible.transport.grpc;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.net.SocketFactory;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels;
import net.phylax.credible.types.SidecarApiModels.ReorgEvent;
import net.phylax.credible.types.SidecarApiModels.ReorgEventReqItem;
import net.phylax.credible.types.SidecarApiModels.SendEventsRequest;
import net.phylax.credible.types.SidecarApiModels.SendEventsResponse;
import net.phylax.credible.types.SidecarApiModels.TransactionReqItem;
import net.phylax.credible.types.SidecarApiModels.TransactionResult;
import net.phylax.credible.utils.ByteUtils;
import sidecar.transport.v1.Sidecar;
import sidecar.transport.v1.SidecarTransportGrpc;


/**
 * gRPC implementation of ISidecarTransport
 * Communicates with the Credible Layer sidecar via gRPC protocol
 *
 * Uses StreamEvents for sending events (transactions, reorg, etc.) through a long-lived stream.
 */
@Slf4j
public class GrpcTransport implements ISidecarTransport {
    private final ManagedChannel channel;
    private final SidecarTransportGrpc.SidecarTransportStub stub;

    private final long deadlineMillis;
    private final CredibleMetricsRegistry metricsRegistry;

    // Stream management for StreamEvents
    private final AtomicReference<StreamObserver<Sidecar.Event>> eventStreamRef;
    private final Object streamLock = new Object();
    private volatile boolean streamConnected = false;

    // Ack tracking for event stream - map event_id to pending ack futures
    private final ConcurrentHashMap<Long, CompletableFuture<Sidecar.StreamAck>> pendingAcks = new ConcurrentHashMap<>();
    // Track send times for ack latency measurement - map event_id to send time in nanos
    private final ConcurrentHashMap<Long, Long> eventSendTimes = new ConcurrentHashMap<>();
    private final AtomicLong eventIdCounter = new AtomicLong(0);
    private static final long ACK_TIMEOUT_BASE_MS = 3;
    private static final long ACK_TIMEOUT_MAX_MS = 100;
    private static final int MAX_RETRIES = 3;

    // Stream management for SubscribeResults
    private final AtomicReference<io.grpc.Context.CancellableContext> resultsSubscriptionContext;
    private volatile boolean resultsSubscriptionActive = false;

    /**
     * Create a new GrpcTransport with pre-configured channel pools
     */
    public GrpcTransport(ManagedChannel channel, long deadlineMillis, CredibleMetricsRegistry metricsRegistry) {
        this.channel = channel;
        this.stub = SidecarTransportGrpc.newStub(channel);
        this.deadlineMillis = deadlineMillis;
        this.metricsRegistry = metricsRegistry;
        this.eventStreamRef = new AtomicReference<>();
        this.resultsSubscriptionContext = new AtomicReference<>();
    }

    /**
     * Create a new GrpcTransport with a single channel (backward compatibility)
     */
    public GrpcTransport(ManagedChannel channel, ManagedChannel pollingChannel, long deadlineMillis, CredibleMetricsRegistry metricsRegistry) {
        this(channel, deadlineMillis, metricsRegistry);
    }

    /**
     * Create a new GrpcTransport connecting to the specified host and port
     */
    public GrpcTransport(String host, int port, long deadlineMillis, CredibleMetricsRegistry metricsRegistry) {
        this(OkHttpChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build(),
            OkHttpChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build(),
            deadlineMillis, metricsRegistry);
    }


    /**
     * Create a new stub from a round-robin selected channel
     */
    private SidecarTransportGrpc.SidecarTransportStub getStub() {
        return stub;
    }

    /**
     * Get or create the event stream for StreamEvents RPC.
     * The stream is lazily initialized and reused across calls.
     */
    private StreamObserver<Sidecar.Event> getOrCreateEventStream() {
        StreamObserver<Sidecar.Event> existing = eventStreamRef.get();
        if (existing != null && streamConnected) {
            return existing;
        }

        synchronized (streamLock) {
            existing = eventStreamRef.get();
            if (existing != null && streamConnected) {
                return existing;
            }

            log.info("Creating new StreamEvents stream");

            StreamObserver<Sidecar.StreamAck> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(Sidecar.StreamAck ack) {
                    // Match ack to pending future using event_id
                    long eventId = ack.getEventId();
                    CompletableFuture<Sidecar.StreamAck> pendingFuture = pendingAcks.remove(eventId);
                    if (pendingFuture != null) {
                        pendingFuture.complete(ack);
                    } else {
                        log.trace("Received ack for unknown event_id={}", eventId);
                    }

                    // Record ack latency metric
                    Long sendTimeNanos = eventSendTimes.remove(eventId);
                    if (sendTimeNanos != null && metricsRegistry != null) {
                        double latencySeconds = (System.nanoTime() - sendTimeNanos) / 1_000_000_000.0;
                        metricsRegistry.getStreamAckLatency().labels().observe(latencySeconds);
                    }

                    if (ack.getSuccess()) {
                        log.trace("StreamAck received: event_id={}, events_processed={}, message={}",
                            eventId, ack.getEventsProcessed(), ack.getMessage());
                    } else {
                        log.warn("StreamAck failure: event_id={}, message={}", eventId, ack.getMessage());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("StreamEvents error: {}", getErrorMessage(t), t);
                    streamConnected = false;
                    eventStreamRef.set(null);
                    // Fail all pending acks
                    pendingAcks.values().forEach(future -> future.completeExceptionally(t));
                    pendingAcks.clear();
                    eventSendTimes.clear();
                }

                @Override
                public void onCompleted() {
                    log.info("StreamEvents completed");
                    streamConnected = false;
                    eventStreamRef.set(null);
                    // Fail all pending acks
                    pendingAcks.values().forEach(future ->
                        future.completeExceptionally(new RuntimeException("Stream completed")));
                    pendingAcks.clear();
                    eventSendTimes.clear();
                }
            };

            StreamObserver<Sidecar.Event> requestObserver = getStub().streamEvents(responseObserver);
            eventStreamRef.set(requestObserver);
            streamConnected = true;

            return requestObserver;
        }
    }

    /**
     * Send an event through the StreamEvents stream with ack confirmation.
     * Sends the event immediately and schedules async retries in the background if needed.
     * This method returns immediately after the first send attempt.
     */
    private void sendEvent(Sidecar.Event event) {
        StreamObserver<Sidecar.Event> stream = getOrCreateEventStream();

        // Generate unique event_id and rebuild event with it
        long eventId = eventIdCounter.incrementAndGet();
        Sidecar.Event eventWithId = event.toBuilder().setEventId(eventId).build();

        CompletableFuture<Sidecar.StreamAck> ackFuture = new CompletableFuture<>();
        pendingAcks.put(eventId, ackFuture);

        log.trace("SendEvent dispatched: event_id={}, event_type={}", eventId, event.getEventCase());

        // Record send time for latency measurement
        eventSendTimes.put(eventId, System.nanoTime());

        synchronized (streamLock) {
            try {
                stream.onNext(eventWithId);
            } catch (Exception e) {
                pendingAcks.remove(eventId);
                eventSendTimes.remove(eventId);
                log.error("Error sending event: {}", e.getMessage(), e);
                streamConnected = false;
                eventStreamRef.set(null);
                throw new RuntimeException("Failed to send event", e);
            }
        }

        // Schedule async ack handling with retries in background (even if the first attempt throws)
        scheduleAckHandling(event, eventId, ackFuture, 0);
    }

    /**
     * Handles ack waiting and retries asynchronously in the background.
     * Does not block the caller. Uses a single ackFuture that stays in pendingAcks
     * until success or all retries exhausted.
     * Uses exponential backoff: timeout = min(base * 2^attempt, max)
     */
    private void scheduleAckHandling(Sidecar.Event event, long eventId, CompletableFuture<Sidecar.StreamAck> ackFuture, int attempt) {
        long timeoutMs = Math.min(ACK_TIMEOUT_BASE_MS * (1L << attempt), ACK_TIMEOUT_MAX_MS);
        CompletableFuture.delayedExecutor(timeoutMs, TimeUnit.MILLISECONDS)
            .execute(() -> {
                if (!ackFuture.isDone()) {
                    // Timeout - ack not received in time
                    if (attempt < MAX_RETRIES) {
                        log.debug("Ack timeout on attempt {} for event_id={}, retrying event async", attempt + 1, eventId);
                        retryEventAsync(event, eventId, ackFuture, attempt + 1);
                    } else {
                        log.warn("Ack timeout after {} retries for event_id={}, proceeding without confirmation", MAX_RETRIES + 1, eventId);
                        pendingAcks.remove(eventId);
                        eventSendTimes.remove(eventId);
                    }
                }
            });
    }

    /**
     * Retry sending an event asynchronously, reusing the same eventId and ackFuture.
     */
    private void retryEventAsync(Sidecar.Event event, long eventId, CompletableFuture<Sidecar.StreamAck> ackFuture, int attempt) {
        StreamObserver<Sidecar.Event> stream = getOrCreateEventStream();

        if (metricsRegistry != null) {
            metricsRegistry.getStreamAckRetryCounter().labels().inc();
        }

        // Reuse the same event_id for retries
        Sidecar.Event eventWithId = event.toBuilder().setEventId(eventId).build();

        synchronized (streamLock) {
            try {
                stream.onNext(eventWithId);
            } catch (Exception e) {
                pendingAcks.remove(eventId);
                eventSendTimes.remove(eventId);
                log.error("Error retrying event on attempt {}: {}", attempt + 1, e.getMessage(), e);
                streamConnected = false;
                eventStreamRef.set(null);
                return;
            }
        }
        scheduleAckHandling(event, eventId, ackFuture, attempt);
    }

    @Override
    public CompletableFuture<SidecarApiModels.SendTransactionsResponse> sendTransactions(SidecarApiModels.SendTransactionsRequest transactions) {
        CompletableFuture<SidecarApiModels.SendTransactionsResponse> future = new CompletableFuture<>();

        try {
            // Convert each transaction to an Event and send through the stream
            for (SidecarApiModels.TransactionExecutionPayload tx : transactions.getTransactions()) {
                TransactionReqItem txItem = new TransactionReqItem(tx);
                Sidecar.Event event = GrpcModelConverter.toProtoEvent(txItem);
                sendEvent(event);
                log.debug("Sent transaction event: txHash={}", ByteUtils.toHex(tx.getTxExecutionId().getTxHash()));
            }

            // For streaming, we complete immediately after sending
            // The actual response comes asynchronously via StreamAck
            future.complete(new SidecarApiModels.SendTransactionsResponse(
                "success",
                "Events sent via stream",
                (long) transactions.getTransactions().size()
            ));
        } catch (Exception e) {
            log.error("Error sending transactions: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<SidecarApiModels.GetTransactionsResponse> getTransactions(SidecarApiModels.GetTransactionsRequest txRequest) {
        CompletableFuture<SidecarApiModels.GetTransactionsResponse> future = new CompletableFuture<>();

        try {
            // Convert to Protobuf
            Sidecar.GetTransactionsRequest request =
                GrpcModelConverter.toProtoGetTransactionsRequest(txRequest);

            log.trace("Calling GetTransactions with {} transactions via gRPC", txRequest.getTxExecutionIds().size());

            // Make async gRPC call with deadline using round-robin channel
            getStub()
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .getTransactions(request, new StreamObserver<Sidecar.GetTransactionsResponse>() {
                    @Override
                    public void onNext(Sidecar.GetTransactionsResponse response) {
                        log.debug("Received GetTransactions response: {} results, {} not found",
                            response.getResultsCount(), response.getNotFoundCount());
                        future.complete(GrpcModelConverter.fromProtoGetTransactionsResponse(response));
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("GetTransactions gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        log.debug("GetTransactions gRPC call completed");
                    }
                });
        } catch (Exception e) {
            log.error("Error preparing GetTransactions request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<SidecarApiModels.GetTransactionResponse> getTransaction(SidecarApiModels.GetTransactionRequest txRequest) {
        CompletableFuture<SidecarApiModels.GetTransactionResponse> future = new CompletableFuture<>();

        try {
            Sidecar.GetTransactionRequest request =
                GrpcModelConverter.toProtoGetTransactionRequest(txRequest);

            log.trace("Calling GetTransaction on {}", txRequest);

            // Make async gRPC call with deadline using round-robin polling channel
            getStub()
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .getTransaction(request, new StreamObserver<Sidecar.GetTransactionResponse>() {
                    @Override
                    public void onNext(Sidecar.GetTransactionResponse response) {
                        SidecarApiModels.GetTransactionResponse result = GrpcModelConverter.fromProtoGetTransactionResponse(response);
                        future.complete(result);
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("GetTransaction gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        log.trace("GetTransaction for {} gRPC call completed", txRequest);
                    }
                });
        } catch (Exception e) {
            log.error("Error preparing GetTransaction request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<SidecarApiModels.ReorgResponse> sendReorg(SidecarApiModels.ReorgRequest reorgRequest) {
        CompletableFuture<SidecarApiModels.ReorgResponse> future = new CompletableFuture<>();

        try {
            // Convert ReorgRequest to ReorgEvent and send through stream
            ReorgEvent reorgEvent = ReorgEvent.fromReorgRequest(reorgRequest);
            ReorgEventReqItem reorgItem = new ReorgEventReqItem(reorgEvent);
            Sidecar.Event event = GrpcModelConverter.toProtoEvent(reorgItem);

            log.trace("Sending reorg via stream: {}", reorgRequest);

            sendEvent(event);

            // For streaming, we complete immediately after sending
            future.complete(new SidecarApiModels.ReorgResponse(true, null));
        } catch (Exception e) {
            log.error("Error sending reorg: {}", e.getMessage(), e);
            future.complete(new SidecarApiModels.ReorgResponse(false, e.getMessage()));
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> subscribeResults(Consumer<TransactionResult> onResult, Consumer<Throwable> onError) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (resultsSubscriptionActive) {
            log.debug("Results subscription already active");
            future.complete(null);
            return future;
        }

        log.info("Creating SubscribeResults stream");

        // Create a cancellable context for the subscription
        io.grpc.Context.CancellableContext cancellableContext = io.grpc.Context.current().withCancellation();
        resultsSubscriptionContext.set(cancellableContext);

        cancellableContext.run(() -> {
            Sidecar.SubscribeResultsRequest request = Sidecar.SubscribeResultsRequest.newBuilder().build();

            getStub().subscribeResults(request, new StreamObserver<Sidecar.TransactionResult>() {
                @Override
                public void onNext(Sidecar.TransactionResult protoResult) {
                    TransactionResult result = GrpcModelConverter.fromProtoTransactionResult(protoResult);
                    log.debug("Received transaction result: txHash={}, status={}",
                        ByteUtils.toHex(result.getTxExecutionId().getTxHash()), result.getStatus());
                    onResult.accept(result);
                }

                @Override
                public void onError(Throwable t) {
                    log.error("SubscribeResults stream error: {}", getErrorMessage(t), t);
                    resultsSubscriptionActive = false;
                    resultsSubscriptionContext.set(null);
                    onError.accept(t);
                }

                @Override
                public void onCompleted() {
                    log.info("SubscribeResults stream completed");
                    resultsSubscriptionActive = false;
                    resultsSubscriptionContext.set(null);
                }
            });

            resultsSubscriptionActive = true;
            future.complete(null);
        });

        return future;
    }

    @Override
    public void closeResultsSubscription() {
        io.grpc.Context.CancellableContext context = resultsSubscriptionContext.getAndSet(null);
        if (context != null) {
            log.info("Closing SubscribeResults stream");
            context.cancel(null);
            resultsSubscriptionActive = false;
        }
    }

    /**
     * Shutdown all gRPC channels gracefully
     */
    public void close() {
        try {
            // Close the results subscription if open
            closeResultsSubscription();

            // Close the event stream if open
            StreamObserver<Sidecar.Event> stream = eventStreamRef.getAndSet(null);
            if (stream != null) {
                try {
                    stream.onCompleted();
                } catch (Exception e) {
                    log.warn("Error closing event stream: {}", e.getMessage());
                }
            }
            streamConnected = false;
        } catch (Exception e) {
            log.warn("Interrupted while shutting down gRPC channels", e);
            channel.shutdown();
        }
    }

    /**
     * Extract a meaningful error message from gRPC exceptions
     */
    private String getErrorMessage(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            StatusRuntimeException sre = (StatusRuntimeException) t;
            Status status = sre.getStatus();
            return String.format("%s: %s", status.getCode(), status.getDescription());
        }
        return t.getMessage();
    }

    /**
     * Builder for creating GrpcTransport instances with custom configuration
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 50051;
        private long deadlineMillis = 5000; // 5 seconds default
        private CredibleMetricsRegistry metricsRegistry;
        
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder deadlineMillis(long deadlineMillis) {
            this.deadlineMillis = deadlineMillis;
            return this;
        }

        public Builder metricsRegistry(CredibleMetricsRegistry metricsRegistry) {
            this.metricsRegistry = metricsRegistry;
            return this;
        }

        public GrpcTransport build() {
            return new GrpcTransport(createChannel(), deadlineMillis, metricsRegistry);
        }

        private ManagedChannel createChannel() {
            SocketFactory flushingSocketFactory = new SocketFactory() {
                private final SocketFactory delegate = SocketFactory.getDefault();
                
                @Override
                public Socket createSocket() throws IOException {
                    Socket socket = delegate.createSocket();
                    socket.setTcpNoDelay(true);
                    return socket;
                }
                
                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    Socket socket = delegate.createSocket(host, port);
                    socket.setTcpNoDelay(true);
                    return socket;
                }
                
                @Override
                public Socket createSocket(String host, int port,
                        java.net.InetAddress localHost, int localPort) throws IOException {
                    Socket socket = delegate.createSocket(host, port, localHost, localPort);
                    socket.setTcpNoDelay(true);
                    return socket;
                }
                
                @Override
                public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
                    Socket socket = delegate.createSocket(host, port);
                    socket.setTcpNoDelay(true);
                    return socket;
                }
                
                @Override
                public Socket createSocket(java.net.InetAddress address, int port,
                        java.net.InetAddress localAddress, int localPort) throws IOException {
                    Socket socket = delegate.createSocket(address, port, localAddress, localPort);
                    socket.setTcpNoDelay(true);
                    return socket;
                }
            };

            OkHttpChannelBuilder channelBuilder = OkHttpChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .socketFactory(flushingSocketFactory)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                // OkHttp-specific: Flow control window
                .flowControlWindow(16 * 1024 * 1024) // 1MB
                // Set max message size
                .maxInboundMetadataSize(8192)
                .maxInboundMessageSize(1 * 1024 * 1024) // 1MB max message
                .intercept(new LoggingClientInterceptor(metricsRegistry));

            return channelBuilder.build();
        }
    }

    @Override
    public CompletableFuture<SendEventsResponse> sendEvents(SendEventsRequest events) {
        CompletableFuture<SendEventsResponse> future = new CompletableFuture<>();

        try {
            // Convert each event item to a proto Event and send through the stream
            for (SidecarApiModels.SendEventsRequestItem item : events.getEvents()) {
                Sidecar.Event event = GrpcModelConverter.toProtoEvent(item);
                sendEvent(event);
            }

            // For streaming, we complete immediately after sending
            future.complete(new SendEventsResponse(
                "accepted",
                "Events sent via stream",
                (long) events.getEvents().size()
            ));
        } catch (Exception e) {
            log.error("Error sending events: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }
}
