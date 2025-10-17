package net.phylax.credible.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.CredibleRejectionReason;
import net.phylax.credible.types.SidecarApiModels.CredibleLayerMethods;
import net.phylax.credible.types.SidecarApiModels.GetTransactionsResponse;
import net.phylax.credible.types.SidecarApiModels.ReorgRequest;
import net.phylax.credible.types.SidecarApiModels.ReorgResponse;
import net.phylax.credible.types.SidecarApiModels.SendBlockEnvRequest;
import net.phylax.credible.types.SidecarApiModels.SendTransactionsRequest;
import net.phylax.credible.types.SidecarApiModels.TransactionResult;
import net.phylax.credible.utils.CredibleLogger;
import net.phylax.credible.utils.Result;

public class DefaultSidecarStrategy implements ISidecarStrategy {
    private static final Logger LOG = CredibleLogger.getLogger(DefaultSidecarStrategy.class);

    private List<ISidecarTransport> primaryTransports = new ArrayList<>();
    private List<ISidecarTransport> activeTransports = new CopyOnWriteArrayList<>();
    private List<ISidecarTransport> fallbackTransports = new CopyOnWriteArrayList<>();

    private AtomicBoolean isActive = new AtomicBoolean(false);

    private Tracer tracer;

    private final Map<String, List<CompletableFuture<GetTransactionsResponse>>> pendingTxRequests = 
        new ConcurrentHashMap<>();

    private int processingTimeout;
    private final CredibleMetricsRegistry metricsRegistry;

    public static class TransportResponse {
        private final ISidecarTransport transport;
        private final boolean success;
        private final String message;
        private final long latencyMs;
        
        public TransportResponse(ISidecarTransport transport, boolean success, String message, long latencyMs) {
            this.transport = transport;
            this.success = success;
            this.message = message;
            this.latencyMs = latencyMs;
        }
        
        public ISidecarTransport getTransport() { return transport; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getLatencyMs() { return latencyMs; }
    }

    public DefaultSidecarStrategy(
        List<ISidecarTransport> primaryTransports,
        List<ISidecarTransport> fallbackTransports,
        int processingTimeout,
        final CredibleMetricsRegistry metricsRegistry,
        final Tracer tracer
    ) {
        this.primaryTransports = primaryTransports;
        this.activeTransports = new CopyOnWriteArrayList<>();
        this.fallbackTransports = fallbackTransports;
        this.processingTimeout = processingTimeout;
        this.metricsRegistry = metricsRegistry;
        this.tracer = tracer;
    }
    
    @Override
    public CompletableFuture<Void> sendBlockEnv(SendBlockEnvRequest blockEnv) {
        var span = tracer.spanBuilder(CredibleLayerMethods.SEND_BLOCK_ENV).startSpan();
        try(Scope scope = span.makeCurrent()) {
            // Send to all primary transports
            Context context = Context.current();
            List<CompletableFuture<TransportResponse>> futures = primaryTransports.stream()
                .map(transport -> sendBlockEnvToTransport(blockEnv, transport))
                .collect(Collectors.toList());
        
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((voidResult, exception) -> {
                    try(Scope primaryTransportScope = context.makeCurrent()) {
                        long failedCount = futures.stream()
                        .filter(f -> !f.isCompletedExceptionally())
                        .map(CompletableFuture::join)
                        .filter(res -> !res.isSuccess())
                        .count();

                        LOG.debug("Checking sidecars: failed: {}, total: {}", failedCount, futures.size());
                        
                        // Check if all active transports failed
                        if (failedCount == futures.size() && !futures.isEmpty()) {
                            LOG.warn("SendBlockEnv to active sidecars failed, trying fallbacks");
                            span.setAttribute("active_transport_target", "fallback");

                            // Send to fallback transports
                            List<CompletableFuture<TransportResponse>> fallbackFutures = fallbackTransports.stream()
                                .map(transport -> sendBlockEnvToTransport(blockEnv, transport))
                                .collect(Collectors.toList());
                                
                            CompletableFuture.allOf(fallbackFutures.toArray(new CompletableFuture[0]))
                                .thenAccept(ignored -> {
                                    try(Scope fallbackTransportScope = context.makeCurrent()) {
                                        updateActiveTransports(extractSuccessfulTransports(fallbackFutures));
                                    } finally {
                                        span.end();
                                    }
                                });
                        } else {
                            // Clear pending requests (since it's the start of the new block)
                            updateActiveTransports(extractSuccessfulTransports(futures));
                            span.setAttribute("active_transport_target", "primary");
                            span.end();
                        }
                    }
            });
        }
    }

    private List<ISidecarTransport> extractSuccessfulTransports(List<CompletableFuture<TransportResponse>> futures) {
        return futures.stream()
            .filter(f -> !f.isCompletedExceptionally())
            .map(CompletableFuture::join)
            .filter(TransportResponse::isSuccess)
            .map(TransportResponse::getTransport)
            .collect(Collectors.toList());
    }

    private void updateActiveTransports(List<ISidecarTransport> successfulTransports) {
        var span = tracer.spanBuilder("updateActiveTransports").startSpan();
        Context context = Context.current().with(span);
        try(Scope scope = context.makeCurrent()) {
            span.setAttribute("transport_count", successfulTransports.size());
            isActive.set(true);
            pendingTxRequests.clear();
            activeTransports.clear();
            activeTransports.addAll(successfulTransports);
            metricsRegistry.registerActiveTransportsGauge(successfulTransports::size);
            LOG.debug("Updated active sidecars - count {}", successfulTransports.size());
        } finally {
            span.end();
        }
    }
    
    private CompletableFuture<TransportResponse> sendBlockEnvToTransport(SendBlockEnvRequest blockEnv, ISidecarTransport transport) {
        long startTime = System.currentTimeMillis();
        var span = tracer.spanBuilder("sendBlockEnvToTransport").startSpan();
        try(Scope scope = span.makeCurrent()) {
            metricsRegistry.getSidecarRpcCounter().labels("sendBlockEnv").inc();
            return transport.sendBlockEnv(blockEnv)
                .thenApply(blockResult -> {
                    long latency = System.currentTimeMillis() - startTime;
                    span.setAttribute("result", blockResult.getStatus());
                    return new TransportResponse(transport, "accepted".equals(blockResult.getStatus()), "Success", latency);
                })
                .exceptionally(ex -> {
                    LOG.debug("SendBlockEnv error: {} - {}",
                        ex.getMessage(),
                        ex.getCause() != null ? ex.getCause().getMessage() : "");
                    span.setAttribute("failed", true);
                    metricsRegistry.getErrorCounter().labels().inc();
                    activeTransports.remove(transport);
                    long latency = System.currentTimeMillis() - startTime;
                    return new TransportResponse(transport, false, ex.getMessage(), latency);
                })
                .whenComplete((res, throwable) -> span.end());
        }
    }
    
    @Override
    public List<CompletableFuture<GetTransactionsResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest) {
        var span = tracer.spanBuilder("dispatchTransactions").startSpan();
        try(Scope scope = span.makeCurrent()) {
            if (activeTransports.isEmpty()) {
                LOG.warn("Active sidecars empty");
                span.setAttribute("failed", true);
                span.end();
                return Collections.emptyList();
            }
    
            List<String> hashes = sendTxRequest.getTransactions().stream()
                .map(tx -> tx.getHash())
                .collect(Collectors.toList());

            Context context = Context.current();
    
            // Calls sendTransactions and chain getTransactions per sidecar
            // In this way we track the send->get request chain per transport without blocking
            List<CompletableFuture<GetTransactionsResponse>> futures = activeTransports.stream()
            .map(transport -> {
                metricsRegistry.getSidecarRpcCounter().labels(CredibleLayerMethods.SEND_TRANSACTIONS).inc();
                
                var sendTxSpan = tracer.spanBuilder(CredibleLayerMethods.SEND_TRANSACTIONS).startSpan();
                return transport.sendTransactions(sendTxRequest)
                    .whenComplete((result, ex) -> {
                        try(Scope sendScope = context.makeCurrent()) {
                            if (ex != null) {
                                LOG.debug("SendTransactions error: {} - {}",
                                    ex.getMessage(),
                                    ex.getCause() != null ? ex.getCause().getMessage() : "");
                                metricsRegistry.getErrorCounter().labels().inc();
                                activeTransports.remove(transport);
                                sendTxSpan.setAttribute("failed", true);
                                span.end();
                            } else {
                                LOG.debug("SendTransactions response: count - {}, message - {}", 
                                    result.getRequestCount(), 
                                    result.getMessage());
                                sendTxSpan.setAttribute("message", result.getMessage());
                            }
                        } finally {
                            sendTxSpan.end();
                        }
                    })
                    .thenCompose(sendResult -> {
                        if (!isActive.get()) {
                            LOG.debug("Transports aren't active!");
                            return CompletableFuture.completedFuture(null);
                        }
    
                        var timing = metricsRegistry.getPollingTimer().labels().startTimer();
                        metricsRegistry.getSidecarRpcCounter().labels("getTransactions").inc();
                        
                        var getTxSpan = tracer.spanBuilder("getTransactions").startSpan();
                        return transport.getTransactions(hashes)
                            .whenComplete((response, throwable) -> {
                                try(Scope getScope = context.makeCurrent()) {
                                    if (throwable != null) {
                                        LOG.debug("GetTransactions error: {} - {}",
                                            throwable.getMessage(),
                                            throwable.getCause() != null ? throwable.getCause().getMessage() : "");
                                        activeTransports.remove(transport);
                                        getTxSpan.setAttribute("failed", true);
                                        span.end();
                                    }
                                    timing.stopTimer();
                                } finally {
                                    getTxSpan.end();
                                    span.end();
                                }
                            });
                    });
            })
            .collect(Collectors.toList());
            
            // NOTE: making the assumption that it's only 1 transaction per request
            // which is implied with the TransactionSelectionPlugin
            pendingTxRequests.put(hashes.get(0), futures);
            
            return futures;
        }
    }
    
    @Override
    public Result<GetTransactionsResponse, CredibleRejectionReason> getTransactionResults(List<String> txHashes) {
        var span = tracer.spanBuilder(CredibleLayerMethods.GET_TRANSACTIONS).startSpan();
        span.setAttribute("active", isActive.get());
        try(Scope scope = span.makeCurrent()) {
            var results = new ArrayList<TransactionResult>();
            var response = new GetTransactionsResponse(results, Collections.emptyList());
    
            // Short circuit if the strategy isn't active
            if (!isActive.get()) {
                return Result.failure(CredibleRejectionReason.NO_ACTIVE_TRANSPORT);
            }
    
            for (String txHash : txHashes) {
                List<CompletableFuture<GetTransactionsResponse>> futures = pendingTxRequests.remove(txHash);
                if (futures == null || futures.isEmpty()) {
                    LOG.debug("No pending request found for transaction {}", txHash);
                    span.setAttribute("failed", true);
                    continue;
                }
                var txResponseResult = handleTransactionFuture(futures);

                if (!txResponseResult.isSuccess()) {
                    LOG.debug("GetTransactionsResponse failed: {}", txResponseResult.getFailure());
                    continue;
                }

                var txResults = txResponseResult.getSuccess().getResults();

                if (txResults.size() == 0) {
                    return Result.failure(CredibleRejectionReason.NO_RESULT);
                }
                
                results.addAll(txResults);

                LOG.debug("GetTransactionsResponse successfully handled: {}", txResults.get(0).getHash());
                span.setAttribute("result_count", txResults.size());
                span.setAttribute("tx_hash", txResults.get(0).getHash());
            }
            return Result.success(response);
        } finally {
            span.end();
        }
    }

    private Result<GetTransactionsResponse, CredibleRejectionReason> handleTransactionFuture(List<CompletableFuture<GetTransactionsResponse>> futures) {
        long startTime = System.currentTimeMillis();
        var span = tracer.spanBuilder("handleTransactionFuture").startSpan();

        CompletableFuture<Result<GetTransactionsResponse, CredibleRejectionReason>> anySuccess = anySuccessOf(futures)
            .orTimeout(processingTimeout, TimeUnit.MILLISECONDS)
            .thenApply(res -> Result.<GetTransactionsResponse, CredibleRejectionReason>success(res))
            .exceptionally(ex -> {
                long latency = System.currentTimeMillis() - startTime;
                LOG.debug("Timeout or error getting transactions: exception {}, latency {}", 
                    ex.getMessage(), latency);
                metricsRegistry.getTimeoutCounter().labels().inc();
                isActive.set(false);
                return Result.failure(CredibleRejectionReason.TIMEOUT);
            });

        try {
            return anySuccess.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.debug("Exception waiting for sidecar responses: {}", e.getMessage());
            metricsRegistry.getErrorCounter().labels().inc();
            span.setAttribute("failed", true);
            return Result.failure(CredibleRejectionReason.ERROR);
        } finally {
            span.end();
        }
    }

    /**
     * Returns the first successful response from all transport getTransactions futures
     * @param futures Futures from the getTransactions transport calls
     * @return
     */
    private CompletableFuture<GetTransactionsResponse> anySuccessOf(List<CompletableFuture<GetTransactionsResponse>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("No futures provided")
            );
        }

        CompletableFuture<GetTransactionsResponse> result = new CompletableFuture<GetTransactionsResponse>();
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<List<Throwable>> exceptions = new AtomicReference<>(
            new CopyOnWriteArrayList<>()
        );

        for (CompletableFuture<GetTransactionsResponse> future : futures) {
            future.whenComplete((value, ex) -> {
                if (ex == null) {
                    result.complete(value);
                } else {
                    LOG.debug("Transport getTransactions failed: {}", ex.getMessage());
                    exceptions.get().add(ex);
                    
                    // If all futures have failed, complete exceptionally
                    if (failureCount.incrementAndGet() == futures.size()) {
                        LOG.debug("All transports completed exceptionally: {}", exceptions.get());
                        CompletionException allFailed = new CompletionException(
                            "All transports failed",
                            // TODO: aggregate exceptions
                            exceptions.get().get(0)
                        );
                        result.completeExceptionally(allFailed);
                    }
                }
            });
        }
        
        return result;
    }

    @Override
    public List<ReorgResponse> sendReorgRequest(ReorgRequest reorgRequest) {
        var span = tracer.spanBuilder("sendReorgRequest").startSpan();
        try(Scope scope = span.makeCurrent()) {
            List<ReorgResponse> successfulResponses = new ArrayList<>();
    
            for (ISidecarTransport transport : activeTransports) {
                try {
                    ReorgResponse response = transport.sendReorg(reorgRequest).join();
                    successfulResponses.add(response);
                    metricsRegistry.getReorgRequestCounter().labels().inc();
                    span.setAttribute("success", response.getSuccess());
                } catch (Exception e) {
                    // Safe to remove with CopyOnWriteArrayList
                    activeTransports.remove(transport);
                    LOG.debug("Exception sending reorg request to transport {}: {}", 
                        transport.toString(), e.getMessage());
                    metricsRegistry.getErrorCounter().labels().inc();
                    span.setAttribute("failed", true);
                }
            }
            return successfulResponses;
        } finally {
            span.end();
        }
    }

    @Override
    public boolean isActive() {
        return isActive.get();
    }
}