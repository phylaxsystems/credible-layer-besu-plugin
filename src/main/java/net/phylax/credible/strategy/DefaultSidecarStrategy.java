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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.CredibleRejectionReason;
import net.phylax.credible.types.SidecarApiModels.CredibleLayerMethods;
import net.phylax.credible.types.SidecarApiModels.GetTransactionRequest;
import net.phylax.credible.types.SidecarApiModels.GetTransactionResponse;
import net.phylax.credible.types.SidecarApiModels.NewIteration;
import net.phylax.credible.types.SidecarApiModels.NewIterationReqItem;
import net.phylax.credible.types.SidecarApiModels.CommitHead;
import net.phylax.credible.types.SidecarApiModels.CommitHeadReqItem;
import net.phylax.credible.types.SidecarApiModels.ReorgRequest;
import net.phylax.credible.types.SidecarApiModels.ReorgResponse;
import net.phylax.credible.types.SidecarApiModels.SendEventsRequest;
import net.phylax.credible.types.SidecarApiModels.SendTransactionsRequest;
import net.phylax.credible.types.SidecarApiModels.TransactionResult;
import net.phylax.credible.types.SidecarApiModels.TxExecutionId;
import net.phylax.credible.utils.ByteUtils;
import net.phylax.credible.utils.Result;

@Slf4j
public class DefaultSidecarStrategy implements ISidecarStrategy {
    private List<ISidecarTransport> primaryTransports;
    private List<ISidecarTransport> activeTransports;
    private List<ISidecarTransport> fallbackTransports;
    // Future that holds the last block env sent to the sidecars

    private AtomicBoolean isActive = new AtomicBoolean(false);
    private final Map<String, Long> blockHashToIterationId = new ConcurrentHashMap<>();

    // Maps a TxExecutionId to a future that will be completed when the result arrives via stream
    private final Map<TxExecutionId, CompletableFuture<GetTransactionResponse>> pendingTxRequests;

    private int processingTimeout;
    // The ratio of the processing timeout that will be used for the stream future (the initial request)
    // The rest is used in the fallback transport
    private float futureTimeoutRatio = 0.8f;
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
        // If transports aren't specified, the strategy shouldn't break
        this.primaryTransports = primaryTransports == null ? new ArrayList<>() : primaryTransports;
        this.fallbackTransports = fallbackTransports == null ? new ArrayList<>() : fallbackTransports;
        this.activeTransports = new CopyOnWriteArrayList<>();
        this.processingTimeout = processingTimeout;
        this.metricsRegistry = metricsRegistry;

        this.pendingTxRequests = new ConcurrentHashMap<>();
    }
    
    @Override
    public CompletableFuture<Void> newIteration(NewIteration iteration) {
        var sendEvents = assembleNewIterationRequest(iteration);

        activeTransports.parallelStream()
            .forEach(transport -> {
                log.debug("Sending iteration {} for block number {}",
                    iteration.getIterationId(),
                    iteration.getBlockEnv().getNumber());
                transport.sendEvents(sendEvents);
            });

        return CompletableFuture.completedFuture(null);
    }

    private SendEventsRequest assembleNewIterationRequest(NewIteration iteration) {
        var sendEvents = new SendEventsRequest();

        var newIteration = new NewIterationReqItem(iteration);
        sendEvents.getEvents().addLast(newIteration);

        return sendEvents;
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
        log.debug("Updated active sidecars - count {}", successfulTransports.size());

        // Subscribe to results stream for each transport
        for (ISidecarTransport transport : successfulTransports) {
            transport.subscribeResults(
                this::onTransactionResult,
                error -> {
                    log.error("Results subscription error: {}", error.getMessage());
                    removeTransport(transport);
                }
            );
        }
    }

    /**
     * Remove transport from the list of active transports. This method centralizes managing active state,
     * metrics and transport list.
     * @param transport Transport to remove
     */
    private void removeTransport(ISidecarTransport transport) {
        activeTransports.remove(transport);
        metricsRegistry.registerActiveTransportsGauge(activeTransports::size);
        if (activeTransports.isEmpty()) {
            isActive.set(false);
        }
    }

    /**
     * Handle incoming transaction result from the SubscribeResults stream.
     * Matches the result to a pending request and completes its future.
     */
    private void onTransactionResult(TransactionResult result) {
        TxExecutionId txExecId = result.getTxExecutionId();
        completePendingRequest(txExecId, new GetTransactionResponse(result), "stream");
    }
    
    private CompletableFuture<TransportResponse> sendCommitHeadToTransport(CommitHead commitHead, ISidecarTransport transport) {
        long startTime = System.currentTimeMillis();
        metricsRegistry.getSidecarRpcCounter().labels(CredibleLayerMethods.SEND_EVENTS).inc();

        return transport.sendEvent(new CommitHeadReqItem(commitHead))
            .thenApply(status -> {
                long latency = System.currentTimeMillis() - startTime;
                return new TransportResponse(transport, status, "Success", latency);
            })
            .exceptionally(ex -> {
                log.debug("NewIteration error: {} - {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getMessage() : "");
                metricsRegistry.getErrorCounter().labels().inc();
                removeTransport(transport);
                long latency = System.currentTimeMillis() - startTime;
                return new TransportResponse(transport, false, ex.getMessage(), latency);
            });
    }
    
    @Override
    public List<CompletableFuture<GetTransactionResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest) {
        if (!isActive.get() || activeTransports.isEmpty()) {
            log.warn("Active sidecars empty");
            return Collections.emptyList();
        }

        List<TxExecutionId> txExecutionIds = sendTxRequest.getTransactions().stream()
            .map(tx -> tx.getTxExecutionId())
            .collect(Collectors.toList());

        List<CompletableFuture<GetTransactionResponse>> resultFutures = txExecutionIds.stream()
            .map(txExecutionId -> {
                CompletableFuture<GetTransactionResponse> resultFuture = new CompletableFuture<>();
                pendingTxRequests.put(txExecutionId, resultFuture);
                return resultFuture;
            })
            .collect(Collectors.toList());

        // Send transactions to all active transports
        for (ISidecarTransport transport : activeTransports) {
            metricsRegistry.getSidecarRpcCounter().labels(CredibleLayerMethods.SEND_TRANSACTIONS).inc();
            transport.sendTransactions(sendTxRequest)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.debug("SendTransactions error: {} - {}",
                            ex.getMessage(),
                            ex.getCause() != null ? ex.getCause().getMessage() : "");
                        metricsRegistry.getErrorCounter().labels().inc();
                        removeTransport(transport);
                    } else {
                        log.debug("SendTransactions response: count - {}, message - {}",
                            result.getRequestCount(),
                            result.getMessage());
                    }
                });
        }

        txExecutionIds.forEach(txExecId ->
            log.debug("Dispatched transaction, waiting for result via stream: hash={}",
                ByteUtils.toHex(txExecId.getTxHash())));

        return resultFutures;
    }
    
    @Override
    public Result<GetTransactionResponse, CredibleRejectionReason> getTransactionResult(GetTransactionRequest transactionRequest) {
        if (!isActive.get()) {
            return Result.failure(CredibleRejectionReason.NO_ACTIVE_TRANSPORT);
        }

        TxExecutionId txExecId = transactionRequest.toTxExecutionId();
        CompletableFuture<GetTransactionResponse> future = pendingTxRequests.get(txExecId);
        if (future == null) {
            log.debug("No pending request found for transaction {}", txExecId);
            return Result.failure(CredibleRejectionReason.NO_RESULT);
        }

        // Use futureTimeoutRatio percentage of timeout for stream, leave the rest for the fallback
        long streamTimeout = (long) (processingTimeout * futureTimeoutRatio);
        long startTime = System.nanoTime();

        try {
            GetTransactionResponse response = future.get(streamTimeout, TimeUnit.MILLISECONDS);
            return Result.success(response);
        } catch (TimeoutException timeoutException) {
            log.debug("Timeout waiting for transaction result via stream, falling back to getTransaction: {}",
                ByteUtils.toHex(txExecId.getTxHash()));
            metricsRegistry.getTimeoutCounter().labels().inc();
            dispatchFallbackLookup(transactionRequest);

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            long remainingTimeout = Math.max(0L, processingTimeout - elapsedMs);

            try {
                GetTransactionResponse response = future.get(remainingTimeout, TimeUnit.MILLISECONDS);
                return Result.success(response);
            } catch (TimeoutException fallbackTimeoutException) {
                log.debug("All fallback attempts failed for transaction: {}", ByteUtils.toHex(txExecId.getTxHash()));
                isActive.set(false);
                return Result.failure(CredibleRejectionReason.PROCESSING_TIMEOUT);
            } catch (InterruptedException e) {
                log.debug("Interrupted waiting for transaction result after fallback: {}",
                    ByteUtils.toHex(txExecId.getTxHash()));
                metricsRegistry.getErrorCounter().labels().inc();
                Thread.currentThread().interrupt();
                return Result.failure(CredibleRejectionReason.ERROR);
            } catch (ExecutionException e) {
                log.debug("Exception waiting for transaction result after fallback: {}, cause: {}",
                    e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "null");
                metricsRegistry.getErrorCounter().labels().inc();
                return Result.failure(CredibleRejectionReason.ERROR);
            }
        } catch (ExecutionException e) {
            log.debug("Exception waiting for transaction result: {}, cause: {}",
                e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "null");
            metricsRegistry.getErrorCounter().labels().inc();
            return Result.failure(CredibleRejectionReason.ERROR);
        } catch (InterruptedException e) {
            log.debug("Interrupted waiting for transaction result: {}", ByteUtils.toHex(txExecId.getTxHash()));
            metricsRegistry.getErrorCounter().labels().inc();
            Thread.currentThread().interrupt();
            return Result.failure(CredibleRejectionReason.ERROR);
        } finally {
            pendingTxRequests.remove(txExecId, future);
        }
    }

    private void dispatchFallbackLookup(GetTransactionRequest transactionRequest) {
        TxExecutionId txExecId = transactionRequest.toTxExecutionId();
        activeTransports.forEach(transport ->
            transport.getTransaction(transactionRequest)
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        log.debug("Fallback getTransaction failed for transport: {}", ex.getMessage());
                        return;
                    }

                    if (hasConcreteResult(response)) {
                        completePendingRequest(txExecId, response, "fallback");
                        return;
                    }

                    log.debug("Fallback getTransaction returned not_found for transaction: {}",
                        ByteUtils.toHex(txExecId.getTxHash()));
                }));
    }

    private void completePendingRequest(
        TxExecutionId txExecId,
        GetTransactionResponse response,
        String source
    ) {
        CompletableFuture<GetTransactionResponse> future = pendingTxRequests.get(txExecId);

        if (future == null) {
            log.trace("Received {} result for unknown/already-completed tx: hash={}",
                source,
                ByteUtils.toHex(txExecId.getTxHash()));
            return;
        }

        if (!hasConcreteResult(response)) {
            log.trace("Ignoring non-terminal {} response for tx: hash={}",
                source,
                ByteUtils.toHex(txExecId.getTxHash()));
            return;
        }

        if (future.complete(response)) {
            log.debug("Received result for pending tx via {}: hash={}, status={}",
                source,
                ByteUtils.toHex(txExecId.getTxHash()),
                response.getResult().getStatus());
            return;
        }

        log.trace("Ignoring {} result for already-completed tx: hash={}",
            source,
            ByteUtils.toHex(txExecId.getTxHash()));
    }

    private boolean hasConcreteResult(GetTransactionResponse response) {
        return response != null && response.getResult() != null;
    }

    @Override
    public CompletableFuture<List<ReorgResponse>> sendReorgRequest(ReorgRequest reorgRequest) {
        List<CompletableFuture<ReorgResponse>> reorgFutures = activeTransports.stream()
            .map(transport -> {
                metricsRegistry.getReorgRequestCounter().labels().inc();
                return transport.sendReorg(reorgRequest)
                    .exceptionally(ex -> {
                        // Safe to remove with CopyOnWriteArrayList
                        removeTransport(transport);
                        log.debug("Exception sending reorg request to transport {}: {}",
                            transport.toString(), ex.getMessage());
                        metricsRegistry.getErrorCounter().labels().inc();
                        return null;
                    });
            })
            .collect(Collectors.toList());

        // Compose all reorg futures into a single future that completes when all resolve
        return CompletableFuture.allOf(reorgFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> reorgFutures.stream()
                .map(CompletableFuture::join)
                .filter(response -> response != null)
                .collect(Collectors.toList()));
    }

    @Override
    public boolean isActive() {
        return isActive.get();
    }

    @Override
    public void setActive(boolean active) {
        isActive.set(active);
    }

    @Override
    public boolean commitHead(CommitHead commitHead, long timeoutMs) {
        if (commitHead == null || commitHead.getBlockHash() == null) {
            log.error("CommitHead not present!");
            return false;
        }

        // Fetch the iteration that maps to the commit head
        String blockHashHex = ByteUtils.toHex(commitHead.getBlockHash());

        if (blockHashHex == null) {
            log.error("Block hash isn't in hex string format!");
            return false;
        }

        var iterationId = blockHashToIterationId.get(blockHashHex);
        blockHashToIterationId.clear();
        commitHead.setSelectedIterationId(iterationId);

        List<CompletableFuture<TransportResponse>> primaryFutures = primaryTransports.stream()
            .map(transport -> sendCommitHeadToTransport(commitHead, transport))
            .collect(Collectors.toList());

        List<CompletableFuture<TransportResponse>> fallbackFutures = fallbackTransports.stream()
            .map(transport -> sendCommitHeadToTransport(commitHead, transport))
            .collect(Collectors.toList());

        CompletableFuture<Void> combinedFutures = CompletableFuture.allOf(
            Stream.concat(primaryFutures.stream(), fallbackFutures.stream()).toArray(CompletableFuture[]::new));
    
        // Else send the new head and recalculate active transports
        combinedFutures
            .whenComplete((voidResult, exception) -> {
                List<ISidecarTransport> successfulPrimaries = extractSuccessfulTransports(primaryFutures);
                List<ISidecarTransport> successfulFallbacks = extractSuccessfulTransports(fallbackFutures);

                if (!successfulPrimaries.isEmpty()) {
                    updateActiveTransports(successfulPrimaries);
                } else if (!successfulFallbacks.isEmpty()) {
                    if (!primaryFutures.isEmpty()) {
                        log.warn("Sending CommitHead to primary sidecars failed, using fallbacks");
                    }
                    updateActiveTransports(successfulFallbacks);
                } else {
                    log.warn("Sending CommitHead failed for all sidecars");
                    isActive.set(false);
                    activeTransports.clear();
                    pendingTxRequests.clear();
                }
        }).join();

        return true;
    }

    @Override
    public void endIteration(String blockhash, Long iterationId) {
        blockHashToIterationId.putIfAbsent(blockhash, iterationId);
    }
}
