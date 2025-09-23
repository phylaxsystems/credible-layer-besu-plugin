package net.phylax.credible.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels.GetTransactionsResponse;
import net.phylax.credible.types.SidecarApiModels.ReorgRequest;
import net.phylax.credible.types.SidecarApiModels.ReorgResponse;
import net.phylax.credible.types.SidecarApiModels.SendBlockEnvRequest;
import net.phylax.credible.types.SidecarApiModels.SendTransactionsRequest;
import net.phylax.credible.types.SidecarApiModels.TransactionResult;

public class DefaultSidecarStrategy implements ISidecarStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSidecarStrategy.class);

    private List<ISidecarTransport> primaryTransports = new ArrayList<>();
    private List<ISidecarTransport> activeTransports = new CopyOnWriteArrayList<>();
    private List<ISidecarTransport> fallbackTransports = new CopyOnWriteArrayList<>();

    private final Map<String, List<CompletableFuture<GetTransactionsResponse>>> pendingTxRequests = 
        new ConcurrentHashMap<>();
    // private final Map<String, TimingContext> pollingTimings = new HashMap<>();

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
        final CredibleMetricsRegistry metricsRegistry) {
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

                LOG.debug("Checking failedCount: {} {}", failedCount, futures.size());
                
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
        pendingTxRequests.clear();
        activeTransports.clear();
        activeTransports.addAll(successfulTransports);
        metricsRegistry.registerActiveTransportsGauge(successfulTransports::size);
        LOG.debug("Updated active sidecars - count {}", successfulTransports.size());
    }
    
    private CompletableFuture<TransportResponse> sendBlockEnvToTransport(SendBlockEnvRequest blockEnv, ISidecarTransport transport) {
        long startTime = System.currentTimeMillis();
        
        metricsRegistry.getSidecarRpcCounter().labels("sendBlockEnv").inc();
        return transport.sendBlockEnv(blockEnv)
            .thenApply(voidResult -> {
                long latency = System.currentTimeMillis() - startTime;
                return new TransportResponse(transport, true, "Success", latency);
            })
            .exceptionally(ex -> {
                LOG.debug("SendBlockEnv error: {} - {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getMessage() : "");
                metricsRegistry.getErrorCounter().labels().inc();
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
        
        // Calls sendTransactions on all active transports in parallel and awaits them
        activeTransports.parallelStream().forEach(transport -> {
            metricsRegistry.getSidecarRpcCounter().labels("sendTransactions").inc();
            transport.sendTransactions(sendTxRequest).whenComplete((result, ex) -> {
                if (ex != null) {
                    // TODO: what to do with the transport?
                    LOG.debug("SendTransactions error: {} - {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getMessage() : "");
                    metricsRegistry.getErrorCounter().labels().inc();
                } else {
                    LOG.debug("SendTransactions response: count - {}, message - {}", 
                        result.getRequestCount(), 
                        result.getMessage());
                }
            }).join();
        });
        
        List<String> hashes = sendTxRequest.getTransactions().stream()
            .map(tx -> tx.getHash())
            .collect(Collectors.toList());

        // Each future in the List holds a response from a single transport
        List<CompletableFuture<GetTransactionsResponse>> futures = activeTransports.stream()
            .map(transport -> {
                var timing = metricsRegistry.getPollingTimer().labels().startTimer();
                metricsRegistry.getSidecarRpcCounter().labels("getTransactions").inc();
                return transport.getTransactions(hashes)
                    .whenComplete((response, throwable) -> {
                        timing.stopTimer();
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
        for (String txHash : txHashes) {
            List<CompletableFuture<GetTransactionsResponse>> futures = pendingTxRequests.remove(txHash);
            if (futures == null || futures.isEmpty()) {
                LOG.debug("No pending request found for transaction {}", txHash);
                continue;
            }
            var txResponse = handleTransactionFuture(futures);

            if (txResponse != null) {
                results.addAll(txResponse.getResults());
            }
        }
        return response;
    }

    private GetTransactionsResponse handleTransactionFuture(List<CompletableFuture<GetTransactionsResponse>> futures) {
        long startTime = System.currentTimeMillis();

        CompletableFuture<GetTransactionsResponse> anySuccess = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(result -> {
                if (result instanceof GetTransactionsResponse) {
                    return (GetTransactionsResponse) result;
                }
                return null;
            })
            .orTimeout(processingTimeout, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                // TODO: what to do with the transport?
                long latency = System.currentTimeMillis() - startTime;
                LOG.debug("Timeout or error getting transactions: latency {} -{}", 
                    latency, ex.getMessage());
                if (ex instanceof TimeoutException) {
                    metricsRegistry.getTimeoutCounter().labels().inc();
                }
                return null;
            });
        
        try {
            GetTransactionsResponse response = anySuccess.get();
            
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                LOG.debug("No valid results from sidecar");
                return null;
            }
            
            LOG.debug("GetTransactionsResponse successfully handled: {}", response.getResults().get(0).getHash());
            return response;
        } catch (InterruptedException | ExecutionException e) {
            LOG.debug("Exception waiting for sidecar responses: {}", e.getMessage());
            metricsRegistry.getErrorCounter().labels().inc();
            return null;
        }
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
                // TODO: what to do with the transport?
                LOG.debug("Exception sending reorg request to transport {}: {}", 
                    transport.toString(), e.getMessage());
                metricsRegistry.getErrorCounter().labels().inc();
            }
        }
        
        return successfulResponses;
    }
}