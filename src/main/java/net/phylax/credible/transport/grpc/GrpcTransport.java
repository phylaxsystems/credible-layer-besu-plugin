package net.phylax.credible.transport.grpc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import net.phylax.credible.types.SidecarApiModels.SendEventsRequest;
import net.phylax.credible.types.SidecarApiModels.SendEventsResponse;
import net.phylax.credible.utils.CredibleLogger;
import sidecar.transport.v1.Sidecar;
import sidecar.transport.v1.SidecarTransportGrpc;


/**
 * gRPC implementation of ISidecarTransport
 * Communicates with the Credible Layer sidecar via gRPC protocol
 *
 * Supports channel pooling with round-robin selection for load distribution
 */
public class GrpcTransport implements ISidecarTransport {
    private static final Logger LOG = CredibleLogger.getLogger(GrpcTransport.class);

    // Channel pool fields (immutable after construction)
    private final ManagedChannel channel;
    private final SidecarTransportGrpc.SidecarTransportStub stub;

    private final long deadlineMillis;
    private final Tracer tracer;
    private final CredibleMetricsRegistry metricsRegistry;

    /**
     * Create a new GrpcTransport with pre-configured channel pools
     */
    public GrpcTransport(ManagedChannel channel, long deadlineMillis, OpenTelemetry openTelemetry, CredibleMetricsRegistry metricsRegistry) {
        this.channel = channel;
        this.stub = SidecarTransportGrpc.newStub(channel);
        this.deadlineMillis = deadlineMillis;
        this.tracer = openTelemetry.getTracer("grpc-transport");
        this.metricsRegistry = metricsRegistry;
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
        this(ManagedChannelBuilder
                .forAddress(host, port)
                .useTransportSecurity()
                .build(),
            ManagedChannelBuilder
                .forAddress(host, port)
                .useTransportSecurity()
                .build(),
            deadlineMillis, openTelemetry, metricsRegistry);
    }


    /**
     * Create a new stub from a round-robin selected channel
     */
    private SidecarTransportGrpc.SidecarTransportStub getStub() {
        return stub;
    }

    @Override
    public CompletableFuture<SidecarApiModels.SendTransactionsResponse> sendTransactions(SidecarApiModels.SendTransactionsRequest transactions) {
        CompletableFuture<SidecarApiModels.SendTransactionsResponse> future = new CompletableFuture<>();

        try {
            Sidecar.SendTransactionsRequest request =
                GrpcModelConverter.toProtoSendTransactionsRequest(transactions);

            // Make async gRPC call with deadline using round-robin channel
            getStub()
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .sendTransactions(request, new StreamObserver<Sidecar.SendTransactionsResponse>() {
                    @Override
                    public void onNext(Sidecar.SendTransactionsResponse response) {
                        SidecarApiModels.SendTransactionsResponse result = GrpcModelConverter.fromProtoSendTransactionsResponse(response);
                        future.complete(result);
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("SendTransactions gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        LOG.trace("SendTransactions gRPC call completed");
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing SendTransactions request: {}", e.getMessage(), e);
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
                        LOG.trace("Received GetTransactions response: {} results, {} not found",
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
                        LOG.trace("GetTransactions gRPC call completed");
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
            // Convert POJO to Protobuf
            Sidecar.ReorgRequest request =
                GrpcModelConverter.toProtoReorgRequest(reorgRequest);

            LOG.trace("Sending reorg via gRPC: blockNumber={}, iterationId={}, txHash={}",
                reorgRequest.getBlockNumber(),
                reorgRequest.getIterationId(),
                reorgRequest.getTxHash());

            // Make async gRPC call with deadline using round-robin channel
            getStub()
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .reorg(request, new StreamObserver<Sidecar.BasicAck>() {
                    @Override
                    public void onNext(Sidecar.BasicAck response) {
                        future.complete(GrpcModelConverter.fromProtoBasicAckToReorgResponse(response));
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("Reorg gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        LOG.trace("Reorg gRPC call completed");
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing Reorg request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Shutdown all gRPC channels gracefully
     */
    public void close() {
        try {
            LOG.info("Shutting down gRPC channels");
            channel.shutdown();
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
        private int channelPoolSize = 1; // Default: single channel (backward compatible)
        
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

        /**
         * Set the number of channels in the write operations pool
         * Default is 1 (single channel, backward compatible)
         */
        public Builder channelPoolSize(int poolSize) {
            if (poolSize < 1) {
                throw new IllegalArgumentException("Channel pool size must be at least 1");
            }
            this.channelPoolSize = poolSize;
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
            Sidecar.SendEvents request = GrpcModelConverter.toProtoSendEvents(events);

            // Make async gRPC call with deadline using round-robin channel
            getStub()
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .sendEvents(request, new StreamObserver<Sidecar.BasicAck>() {
                    @Override
                    public void onNext(Sidecar.BasicAck response) {
                        SendEventsResponse result = GrpcModelConverter.fromProtoBasicAckToSendEventsResponse(response);
                        future.complete(result);
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("SendEvents gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        LOG.trace("SendEvents gRPC call completed");
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing SendEvents request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }
}
