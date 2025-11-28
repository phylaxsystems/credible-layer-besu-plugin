package net.phylax.credible.transport.grpc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels;
import net.phylax.credible.types.SidecarApiModels.CredibleLayerMethods;
import net.phylax.credible.types.SidecarApiModels.ReorgEvent;
import net.phylax.credible.types.SidecarApiModels.ReorgEventReqItem;
import net.phylax.credible.types.SidecarApiModels.SendEventsRequest;
import net.phylax.credible.types.SidecarApiModels.SendEventsResponse;
import net.phylax.credible.types.SidecarApiModels.TransactionReqItem;
import net.phylax.credible.types.SidecarApiModels.TransactionResult;
import net.phylax.credible.utils.CredibleLogger;
import sidecar.transport.v1.Sidecar;
import sidecar.transport.v1.SidecarTransportGrpc;


/**
 * gRPC implementation of ISidecarTransport
 * Communicates with the Credible Layer sidecar via gRPC protocol
 *
 * Uses StreamEvents for sending events (transactions, reorg, etc.) through a long-lived stream.
 */
public class GrpcTransport implements ISidecarTransport {
    private static final Logger LOG = CredibleLogger.getLogger(GrpcTransport.class);

    private final ManagedChannel channel;
    private final SidecarTransportGrpc.SidecarTransportStub stub;

    private final long deadlineMillis;
    private final Tracer tracer;
    private final CredibleMetricsRegistry metricsRegistry;

    // Stream management for StreamEvents
    private final AtomicReference<StreamObserver<Sidecar.Event>> eventStreamRef;
    private final Object streamLock = new Object();
    private volatile boolean streamConnected = false;

    // Stream management for SubscribeResults
    private final AtomicReference<io.grpc.Context.CancellableContext> resultsSubscriptionContext;
    private volatile boolean resultsSubscriptionActive = false;

    /**
     * Create a new GrpcTransport with pre-configured channel pools
     */
    public GrpcTransport(ManagedChannel channel, long deadlineMillis, OpenTelemetry openTelemetry, CredibleMetricsRegistry metricsRegistry) {
        this.channel = channel;
        this.stub = SidecarTransportGrpc.newStub(channel);
        this.deadlineMillis = deadlineMillis;
        this.tracer = openTelemetry.getTracer("grpc-transport");
        this.metricsRegistry = metricsRegistry;
        this.eventStreamRef = new AtomicReference<>();
        this.resultsSubscriptionContext = new AtomicReference<>();
    }

    /**
     * Create a new GrpcTransport with a single channel (backward compatibility)
     */
    public GrpcTransport(ManagedChannel channel, ManagedChannel pollingChannel, long deadlineMillis, OpenTelemetry openTelemetry, CredibleMetricsRegistry metricsRegistry) {
        this(channel, deadlineMillis, openTelemetry, metricsRegistry);
    }

    /**
     * Create a new GrpcTransport connecting to the specified host and port
     */
    public GrpcTransport(String host, int port, long deadlineMillis, OpenTelemetry openTelemetry, CredibleMetricsRegistry metricsRegistry) {
        this(OkHttpChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build(),
            OkHttpChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build(),
            deadlineMillis, openTelemetry, metricsRegistry);
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

            LOG.info("Creating new StreamEvents stream");

            StreamObserver<Sidecar.StreamAck> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(Sidecar.StreamAck ack) {
                    if (ack.getSuccess()) {
                        LOG.trace("StreamAck received: events_processed={}, message={}",
                            ack.getEventsProcessed(), ack.getMessage());
                    } else {
                        LOG.warn("StreamAck failure: message={}", ack.getMessage());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    LOG.error("StreamEvents error: {}", getErrorMessage(t), t);
                    streamConnected = false;
                    eventStreamRef.set(null);
                }

                @Override
                public void onCompleted() {
                    LOG.info("StreamEvents completed");
                    streamConnected = false;
                    eventStreamRef.set(null);
                }
            };

            StreamObserver<Sidecar.Event> requestObserver = getStub().streamEvents(responseObserver);
            eventStreamRef.set(requestObserver);
            streamConnected = true;

            return requestObserver;
        }
    }

    /**
     * Send an event through the StreamEvents stream.
     */
    private void sendEvent(Sidecar.Event event) {
        StreamObserver<Sidecar.Event> stream = getOrCreateEventStream();
        try {
            stream.onNext(event);
        } catch (Exception e) {
            LOG.error("Error sending event: {}", e.getMessage(), e);
            streamConnected = false;
            eventStreamRef.set(null);
            throw e;
        }
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
                LOG.debug("Sent transaction event: txHash={}", tx.getTxExecutionId().getTxHash());
            }

            // For streaming, we complete immediately after sending
            // The actual response comes asynchronously via StreamAck
            future.complete(new SidecarApiModels.SendTransactionsResponse(
                "success",
                "Events sent via stream",
                (long) transactions.getTransactions().size()
            ));
        } catch (Exception e) {
            LOG.error("Error sending transactions: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<SidecarApiModels.GetTransactionsResponse> getTransactions(SidecarApiModels.GetTransactionsRequest txRequest) {
        var span = tracer.spanBuilder(CredibleLayerMethods.GET_TRANSACTIONS).startSpan();
        CompletableFuture<SidecarApiModels.GetTransactionsResponse> future = new CompletableFuture<>();

        try {
            // Convert to Protobuf
            Sidecar.GetTransactionsRequest request =
                GrpcModelConverter.toProtoGetTransactionsRequest(txRequest);

            LOG.trace("Getting {} transactions via gRPC", txRequest.getTxExecutionIds().size());

            // Make async gRPC call with deadline using round-robin channel
            getStub()
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .getTransactions(request, new StreamObserver<Sidecar.GetTransactionsResponse>() {
                    @Override
                    public void onNext(Sidecar.GetTransactionsResponse response) {
                        LOG.debug("Received GetTransactions response: {} results, {} not found",
                            response.getResultsCount(), response.getNotFoundCount());
                        future.complete(GrpcModelConverter.fromProtoGetTransactionsResponse(response));
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("GetTransactions gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                        span.setAttribute("failed", true);
                        span.end();
                    }

                    @Override
                    public void onCompleted() {
                        LOG.debug("GetTransactions gRPC call completed");
                        span.end();
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing GetTransactions request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
            span.end();
        }

        return future;
    }

    @Override
    public CompletableFuture<SidecarApiModels.GetTransactionResponse> getTransaction(SidecarApiModels.GetTransactionRequest txRequest) {
        CompletableFuture<SidecarApiModels.GetTransactionResponse> future = new CompletableFuture<>();

        try {
            Sidecar.GetTransactionRequest request =
                GrpcModelConverter.toProtoGetTransactionRequest(txRequest);

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
                        LOG.error("GetTransaction gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        LOG.trace("GetTransaction gRPC call completed");
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing GetTransaction request: {}", e.getMessage(), e);
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

            LOG.trace("Sending reorg via stream: blockNumber={}, iterationId={}, txHash={}",
                reorgRequest.getBlockNumber(),
                reorgRequest.getIterationId(),
                reorgRequest.getTxHash());

            sendEvent(event);

            // For streaming, we complete immediately after sending
            future.complete(new SidecarApiModels.ReorgResponse(true, null));
        } catch (Exception e) {
            LOG.error("Error sending reorg: {}", e.getMessage(), e);
            future.complete(new SidecarApiModels.ReorgResponse(false, e.getMessage()));
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> subscribeResults(Consumer<TransactionResult> onResult, Consumer<Throwable> onError) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (resultsSubscriptionActive) {
            LOG.debug("Results subscription already active");
            future.complete(null);
            return future;
        }

        LOG.info("Creating SubscribeResults stream");

        // Create a cancellable context for the subscription
        io.grpc.Context.CancellableContext cancellableContext = io.grpc.Context.current().withCancellation();
        resultsSubscriptionContext.set(cancellableContext);

        cancellableContext.run(() -> {
            Sidecar.SubscribeResultsRequest request = Sidecar.SubscribeResultsRequest.newBuilder().build();

            getStub().subscribeResults(request, new StreamObserver<Sidecar.TransactionResult>() {
                @Override
                public void onNext(Sidecar.TransactionResult protoResult) {
                    TransactionResult result = GrpcModelConverter.fromProtoTransactionResult(protoResult);
                    LOG.debug("Received transaction result: txHash={}, status={}",
                        result.getTxExecutionId().getTxHash(), result.getStatus());
                    onResult.accept(result);
                }

                @Override
                public void onError(Throwable t) {
                    LOG.error("SubscribeResults stream error: {}", getErrorMessage(t), t);
                    resultsSubscriptionActive = false;
                    resultsSubscriptionContext.set(null);
                    onError.accept(t);
                }

                @Override
                public void onCompleted() {
                    LOG.info("SubscribeResults stream completed");
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
            LOG.info("Closing SubscribeResults stream");
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
                    LOG.warn("Error closing event stream: {}", e.getMessage());
                }
            }
            streamConnected = false;
        } catch (Exception e) {
            LOG.warn("Interrupted while shutting down gRPC channels", e);
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
        private OpenTelemetry openTelemetry = OpenTelemetry.noop();
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

        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
            return this;
        }

        public Builder metricsRegistry(CredibleMetricsRegistry metricsRegistry) {
            this.metricsRegistry = metricsRegistry;
            return this;
        }

        public GrpcTransport build() {
            return new GrpcTransport(createChannel(), deadlineMillis, openTelemetry, metricsRegistry);
        }

        private ManagedChannel createChannel() {
            OkHttpChannelBuilder channelBuilder = OkHttpChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
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
            LOG.error("Error sending events: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }
}
