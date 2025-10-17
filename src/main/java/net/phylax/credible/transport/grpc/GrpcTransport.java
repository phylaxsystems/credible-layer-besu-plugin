package net.phylax.credible.transport.grpc;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels;
import net.phylax.credible.types.SidecarApiModels.CredibleLayerMethods;
import sidecar.transport.v1.Sidecar;
import sidecar.transport.v1.SidecarTransportGrpc;

/**
 * gRPC implementation of ISidecarTransport
 * Communicates with the Credible Layer sidecar via gRPC protocol
 */
public class GrpcTransport implements ISidecarTransport {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcTransport.class);

    private final ManagedChannel channel;
    private final SidecarTransportGrpc.SidecarTransportStub asyncStub;
    private final long deadlineMillis;
    private final Tracer tracer;

    /**
     * Create a new GrpcTransport with a pre-configured channel
     */
    public GrpcTransport(ManagedChannel channel, long deadlineMillis, OpenTelemetry openTelemetry) {
        this.channel = channel;
        this.asyncStub = SidecarTransportGrpc.newStub(channel);
        this.deadlineMillis = deadlineMillis;
        this.tracer = openTelemetry.getTracer("grpc-transport");
    }

    /**
     * Create a new GrpcTransport connecting to the specified host and port
     */
    public GrpcTransport(String host, int port, long deadlineMillis, OpenTelemetry openTelemetry) {
        this(ManagedChannelBuilder
            .forAddress(host, port)
            .useTransportSecurity()
            .build(), deadlineMillis, openTelemetry);
    }

    @Override
    public CompletableFuture<SidecarApiModels.SendBlockEnvResponse> sendBlockEnv(SidecarApiModels.SendBlockEnvRequest blockEnv) {
        var span = tracer.spanBuilder(CredibleLayerMethods.SEND_BLOCK_ENV).startSpan();
        CompletableFuture<SidecarApiModels.SendBlockEnvResponse> future = new CompletableFuture<>();

        try {
            // Convert POJO to Protobuf
            Sidecar.BlockEnvEnvelope request = GrpcModelConverter.toProtoBlockEnvEnvelope(blockEnv);

            LOG.trace("Sending BlockEnv via gRPC: number={}", blockEnv.getNumber());

            // Make async gRPC call with deadline
            asyncStub
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .sendBlockEnv(request, new StreamObserver<Sidecar.BasicAck>() {
                    @Override
                    public void onNext(Sidecar.BasicAck response) {
                        LOG.trace("Received BlockEnv response: accepted={}", response.getAccepted());
                        future.complete(GrpcModelConverter.fromProtoBasicAckToBlockEnvResponse(response));
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("SendBlockEnv gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                        span.setAttribute("failed", true);
                        span.end();
                    }

                    @Override
                    public void onCompleted() {
                        LOG.trace("SendBlockEnv gRPC call completed");
                        span.end();
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing SendBlockEnv request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
            span.end();
        }

        return future;
    }

    @Override
    public CompletableFuture<SidecarApiModels.SendTransactionsResponse> sendTransactions(SidecarApiModels.SendTransactionsRequest transactions) {
        var span = tracer.spanBuilder(CredibleLayerMethods.SEND_TRANSACTIONS).startSpan();
        CompletableFuture<SidecarApiModels.SendTransactionsResponse> future = new CompletableFuture<>();

        try {
            // Convert POJO to Protobuf
            Sidecar.SendTransactionsRequest request =
                GrpcModelConverter.toProtoSendTransactionsRequest(transactions);

            LOG.trace("Sending {} transactions via gRPC", transactions.getTransactions().size());

            // Make async gRPC call with deadline
            asyncStub
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .sendTransactions(request, new StreamObserver<Sidecar.SendTransactionsResponse>() {
                    @Override
                    public void onNext(Sidecar.SendTransactionsResponse response) {
                        LOG.trace("Received SendTransactions response: acceptedCount={}, requestCount={}",
                            response.getAcceptedCount(), response.getRequestCount());
                        future.complete(GrpcModelConverter.fromProtoSendTransactionsResponse(response));
                        span.setAttribute("accepted_count", response.getAcceptedCount());
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("SendTransactions gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                        span.setAttribute("failed", true);
                        span.end();
                    }

                    @Override
                    public void onCompleted() {
                        LOG.trace("SendTransactions gRPC call completed");
                        span.end();
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing SendTransactions request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
            span.end();
        }

        return future;
    }

    @Override
    public CompletableFuture<SidecarApiModels.GetTransactionsResponse> getTransactions(List<String> txHashes) {
        var span = tracer.spanBuilder(CredibleLayerMethods.GET_TRANSACTIONS).startSpan();
        CompletableFuture<SidecarApiModels.GetTransactionsResponse> future = new CompletableFuture<>();

        try {
            // Convert to Protobuf
            Sidecar.GetTransactionsRequest request =
                GrpcModelConverter.toProtoGetTransactionsRequest(txHashes);

            LOG.trace("Getting {} transactions via gRPC", txHashes.size());

            // Make async gRPC call with deadline
            asyncStub
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
    public CompletableFuture<SidecarApiModels.GetTransactionResponse> getTransaction(String txHash) {
        var span = tracer.spanBuilder(CredibleLayerMethods.GET_TRANSACTION).startSpan();
        CompletableFuture<SidecarApiModels.GetTransactionResponse> future = new CompletableFuture<>();

        try {
            // Convert to Protobuf
            Sidecar.GetTransactionRequest request =
                GrpcModelConverter.toProtoGetTransactionRequest(txHash);

            LOG.trace("Getting {} transaction via gRPC", txHash);

            // Make async gRPC call with deadline
            asyncStub
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .getTransaction(request, new StreamObserver<Sidecar.GetTransactionResponse>() {
                    @Override
                    public void onNext(Sidecar.GetTransactionResponse response) {
                        LOG.trace("Received GetTransaction response: {} results",
                            response.getResult());
                        future.complete(GrpcModelConverter.fromProtoGetTransactionResponse(response));
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("GetTransaction gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                        span.setAttribute("failed", true);
                        span.end();
                    }

                    @Override
                    public void onCompleted() {
                        LOG.trace("GetTransaction gRPC call completed");
                        span.end();
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing GetTransaction request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
            span.end();
        }

        return future;
    }

    @Override
    public CompletableFuture<SidecarApiModels.ReorgResponse> sendReorg(SidecarApiModels.ReorgRequest reorgRequest) {
        var span = tracer.spanBuilder(CredibleLayerMethods.SEND_REORG).startSpan();
        CompletableFuture<SidecarApiModels.ReorgResponse> future = new CompletableFuture<>();

        try {
            // Convert POJO to Protobuf
            Sidecar.ReorgRequest request =
                GrpcModelConverter.toProtoReorgRequest(reorgRequest);

            LOG.trace("Sending reorg via gRPC: removedTxHash={}", reorgRequest.getRemovedTxHash());

            // Make async gRPC call with deadline
            asyncStub
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .reorg(request, new StreamObserver<Sidecar.BasicAck>() {
                    @Override
                    public void onNext(Sidecar.BasicAck response) {
                        LOG.trace("Received Reorg response: accepted={}", response.getAccepted());
                        future.complete(GrpcModelConverter.fromProtoBasicAckToReorgResponse(response));
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("Reorg gRPC error: {}", getErrorMessage(t), t);
                        future.completeExceptionally(t);
                        span.setAttribute("failed", true);
                        span.end();
                    }

                    @Override
                    public void onCompleted() {
                        LOG.trace("Reorg gRPC call completed");
                        span.end();
                    }
                });
        } catch (Exception e) {
            LOG.error("Error preparing Reorg request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
            span.end();
        }

        return future;
    }

    /**
     * Shutdown the gRPC channel gracefully
     */
    public void close() {
        try {
            LOG.info("Shutting down gRPC channel");
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while shutting down gRPC channel", e);
            channel.shutdownNow();
            Thread.currentThread().interrupt();
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
        private boolean useTls = false;
        private OpenTelemetry openTelemetry = OpenTelemetry.noop();

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

        public Builder useTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
            return this;
        }

        public GrpcTransport build() {
            ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
                .forAddress(host, port);

            if (useTls) {
                channelBuilder.useTransportSecurity();
            } else {
                channelBuilder.usePlaintext();
            }

            GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(openTelemetry);
            channelBuilder.intercept(grpcTelemetry.newClientInterceptor());

            ManagedChannel channel = channelBuilder.build();
            return new GrpcTransport(channel, deadlineMillis, openTelemetry);
        }
    }
}