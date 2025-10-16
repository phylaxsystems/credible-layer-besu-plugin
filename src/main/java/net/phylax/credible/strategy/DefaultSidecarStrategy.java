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
import org.slf4j.LoggerFactory;

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
import net.phylax.credible.utils.Result;

public class DefaultSidecarStrategy implements ISidecarStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSidecarStrategy.class);

    private List<ISidecarTransport> primaryTransports = new ArrayList<>();
    private List<ISidecarTransport> activeTransports = new CopyOnWriteArrayList<>();
    private List<ISidecarTransport> fallbackTransports = new CopyOnWriteArrayList<>();

    private AtomicBoolean isActive = new AtomicBoolean(false);

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
        final CredibleMetricsRegistry metricsRegistry
    ) {
        this.primaryTransports = primaryTransports;
        this.activeTransports = new CopyOnWriteArrayList<>();
        this.fallbackTransports = fallbackTransports;
        this.processingTimeout = processingTimeout;
        this.metricsRegistry = metricsRegistry;
    }
    
    @Override
    public CompletableFuture<Void> sendBlockEnv(SendBlockEnvRequest blockEnv) {
        // Send to all primary transports
        List<CompletableFuture<TransportResponse>> futures = primaryTransports.stream()
            .map(transport -> sendBlockEnvToTransport(blockEnv, transport))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((voidResult, exception) -> {
                long failedCount = futures.stream()
                    .filter(f -> !f.isCompletedExceptionally())
                    .map(CompletableFuture::join)
                    .filter(res -> !res.isSuccess())
                    .count();

                LOG.debug("Checking sidecars: failed: {}, total: {}", failedCount, futures.size());
                
                // Check if all active transports failed
                if (failedCount == futures.size() && !futures.isEmpty()) {
                    LOG.warn("SendBlockEnv to active sidecars failed, trying fallbacks");

                    // Send to fallback transports
                    List<CompletableFuture<TransportResponse>> fallbackFutures = fallbackTransports.stream()
                        .map(transport -> sendBlockEnvToTransport(blockEnv, transport))
                        .collect(Collectors.toList());
                        
                    CompletableFuture.allOf(fallbackFutures.toArray(new CompletableFuture[0]))
                        .thenAccept(ignored -> {
                            updateActiveTransports(extractSuccessfulTransports(fallbackFutures));
                        });
                } else {
                    // Clear pending requests (since it's the start of the new block)
                    updateActiveTransports(extractSuccessfulTransports(futures));
                }
            });
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
        isActive.set(true);
        pendingTxRequests.clear();
        activeTransports.clear();
        activeTransports.addAll(successfulTransports);
        metricsRegistry.registerActiveTransportsGauge(successfulTransports::size);
        LOG.debug("Updated active sidecars - count {}", successfulTransports.size());
    }
    
    private CompletableFuture<TransportResponse> sendBlockEnvToTransport(SendBlockEnvRequest blockEnv, ISidecarTransport transport) {
        long startTime = System.currentTimeMillis();
        
        metricsRegistry.getSidecarRpcCounter().labels(CredibleLayerMethods.SEND_BLOCK_ENV).inc();
        return transport.sendBlockEnv(blockEnv)
            .thenApply(blockResult -> {
                long latency = System.currentTimeMillis() - startTime;
                return new TransportResponse(transport, "accepted".equals(blockResult.getStatus()), "Success", latency);
            })
            .exceptionally(ex -> {
                LOG.debug("SendBlockEnv error: {} - {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getMessage() : "");
                metricsRegistry.getErrorCounter().labels().inc();
                activeTransports.remove(transport);
                long latency = System.currentTimeMillis() - startTime;
                return new TransportResponse(transport, false, ex.getMessage(), latency);
            });
    }
    
    @Override
    public List<CompletableFuture<GetTransactionsResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest) {
        
        if (activeTransports.isEmpty()) {
            LOG.warn("Active sidecars empty");
            return Collections.emptyList();
        }

        List<String> hashes = sendTxRequest.getTransactions().stream()
            .map(tx -> tx.getHash())
            .collect(Collectors.toList());

        // Calls sendTransactions and chain getTransactions per sidecar
        // In this way we track the send->get request chain per transport without blocking
        List<CompletableFuture<GetTransactionsResponse>> futures = activeTransports.stream()
        .map(transport -> {
            metricsRegistry.getSidecarRpcCounter().labels(CredibleLayerMethods.SEND_TRANSACTIONS).inc();
            
            return transport.sendTransactions(sendTxRequest)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.debug("SendTransactions error: {} - {}",
                            ex.getMessage(),
                            ex.getCause() != null ? ex.getCause().getMessage() : "");
                        metricsRegistry.getErrorCounter().labels().inc();
                        activeTransports.remove(transport);
                    } else {
                        LOG.debug("SendTransactions response: count - {}, message - {}", 
                            result.getRequestCount(), 
                            result.getMessage());
                    }
                })
                .thenCompose(sendResult -> {
                    if (!isActive.get()) {
                        LOG.debug("Transports aren't active!");
                        return CompletableFuture.completedFuture(null);
                    }

                    var timing = metricsRegistry.getPollingTimer().labels().startTimer();
                    metricsRegistry.getSidecarRpcCounter().labels(CredibleLayerMethods.GET_TRANSACTIONS).inc();
                    
                    return transport.getTransactions(hashes)
                        .whenComplete((response, throwable) -> {
                            if (throwable != null) {
                                LOG.debug("GetTransactions error: {} - {}",
                                    throwable.getMessage(),
                                    throwable.getCause() != null ? throwable.getCause().getMessage() : "");
                                activeTransports.remove(transport);
                            }
                            timing.stopTimer();
                        });
                });
        })
        .collect(Collectors.toList());
        
        // NOTE: making the assumption that it's only 1 transaction per request
        // which is implied with the TransactionSelectionPlugin
        pendingTxRequests.put(hashes.get(0), futures);
        
        return futures;
    }
    
    @Override
    public GetTransactionsResponse getTransactionResults(List<String> txHashes) {
        var results = new ArrayList<TransactionResult>();
        var response = new GetTransactionsResponse(results, Collections.emptyList());

        // Short circuit if the strategy isn't active
        if (!isActive.get()) {
            return response;
        }

        for (String txHash : txHashes) {
            List<CompletableFuture<GetTransactionsResponse>> futures = pendingTxRequests.remove(txHash);
            if (futures == null || futures.isEmpty()) {
                LOG.debug("No pending request found for transaction {}", txHash);
                continue;
            }

            Result<GetTransactionsResponse, CredibleRejectionReason> txResponseResult = handleTransactionFuture(futures);

            if (txResponseResult.isSuccess()) {
                results.addAll(txResponseResult.getSuccess().getResults());
                continue;
            }

            LOG.debug("Transaction {} rejected: {}", txHash, txResponseResult.getFailure());
        }
        return response;
    }
    
    private Result<GetTransactionsResponse, CredibleRejectionReason> handleTransactionFuture(List<CompletableFuture<GetTransactionsResponse>> futures) {
        long startTime = System.currentTimeMillis();

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
            Result<GetTransactionsResponse, CredibleRejectionReason> response = anySuccess.get();

            if (response.isSuccess() && response.getSuccess().getResults().isEmpty()) {
                return Result.failure(CredibleRejectionReason.NO_RESULT);
            }
            
            return response;
        } catch (InterruptedException | ExecutionException e) {
            LOG.debug("Exception waiting for sidecar responses: {}", e.getMessage());
            metricsRegistry.getErrorCounter().labels().inc();
            return Result.failure(CredibleRejectionReason.ERROR);
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
        List<ReorgResponse> successfulResponses = new ArrayList<>();
    
        for (ISidecarTransport transport : activeTransports) {
            try {
                ReorgResponse response = transport.sendReorg(reorgRequest).join();
                successfulResponses.add(response);
                metricsRegistry.getReorgRequestCounter().labels().inc();
            } catch (Exception e) {
                // Safe to remove with CopyOnWriteArrayList
                 activeTransports.remove(transport);
                LOG.debug("Exception sending reorg request to transport {}: {}", 
                    transport.toString(), e.getMessage());
                metricsRegistry.getErrorCounter().labels().inc();
            }
        }
        
        return successfulResponses;
    }

    @Override
    public boolean isActive() {
        return isActive.get();
    }
}