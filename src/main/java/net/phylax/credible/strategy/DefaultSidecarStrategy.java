package net.phylax.credible.strategy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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
import net.phylax.credible.utils.CredibleLogger;
import net.phylax.credible.utils.Result;

public class DefaultSidecarStrategy implements ISidecarStrategy {
    private static final Logger LOG = CredibleLogger.getLogger(DefaultSidecarStrategy.class);

    private List<ISidecarTransport> primaryTransports;
    private List<ISidecarTransport> activeTransports;
    private List<ISidecarTransport> fallbackTransports;
    // Future that holds the last block env sent to the sidecars
    private Optional<CommitHead> maybeNewHead = Optional.empty();

    private AtomicBoolean isActive = new AtomicBoolean(false);
    private final Map<String, Long> blockHashToIterationId = new ConcurrentHashMap<>();

    private Tracer tracer;

    // Maps a TxExecutionId to a future that will be completed when the result arrives via stream
    private final Map<TxExecutionId, CompletableFuture<GetTransactionResponse>> pendingTxRequests;

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

        this.pendingTxRequests = new ConcurrentHashMap<>();
    }
    
    @Override
    public CompletableFuture<Void> newIteration(NewIteration iteration) {
        var span = tracer.spanBuilder(CredibleLayerMethods.NEW_ITERATION).startSpan();
        try(Scope scope = span.makeCurrent()) {
            Context context = Context.current();

            Optional<CommitHead> commitHead = maybeNewHead;
            var sendEvents = assembleNewIterationRequest(iteration);
            maybeNewHead = Optional.empty();

            List<CompletableFuture<TransportResponse>> primaryFutures = primaryTransports.stream()
                .map(transport -> sendIterationToTransport(sendEvents, transport))
                .collect(Collectors.toList());

            List<CompletableFuture<TransportResponse>> fallbackFutures = fallbackTransports.stream()
                .map(transport -> sendIterationToTransport(sendEvents, transport))
                .collect(Collectors.toList());

            commitHead.ifPresent(head -> {
                LOG.debug("Sending commit_head {} with {} transactions and iteration {} for block number {}",
                    head.getBlockNumber(),
                    head.getNTransactions(),
                    iteration.getIterationId(),
                    iteration.getBlockEnv().getNumber());
            });

            LOG.debug("Sending iteration {} for block number {} to {} primaries and {} fallbacks",
                iteration.getIterationId(),
                iteration.getBlockEnv().getNumber(),
                primaryFutures.size(),
                fallbackFutures.size());

            CompletableFuture<Void> combinedFutures = CompletableFuture.allOf(
                Stream.concat(primaryFutures.stream(), fallbackFutures.stream()).toArray(CompletableFuture[]::new));

            if (commitHead.isEmpty()) {
                combinedFutures.whenComplete((ignored, throwable) -> span.end()).join();
                return CompletableFuture.completedFuture(null);
            }
        
            // Else send the new head and recalculate active transports
            combinedFutures
                .whenComplete((voidResult, exception) -> {
                    try(Scope primaryTransportScope = context.makeCurrent()) {
                        List<ISidecarTransport> successfulPrimaries = extractSuccessfulTransports(primaryFutures);
                        List<ISidecarTransport> successfulFallbacks = extractSuccessfulTransports(fallbackFutures);

                        if (!successfulPrimaries.isEmpty()) {
                            updateActiveTransports(successfulPrimaries);
                            span.setAttribute("active_transport_target", "primary");
                        } else if (!successfulFallbacks.isEmpty()) {
                            if (!primaryFutures.isEmpty()) {
                                LOG.warn("Sending CommitHead to primary sidecars failed, using fallbacks");
                            }
                            updateActiveTransports(successfulFallbacks);
                            span.setAttribute("active_transport_target", "fallback");
                        } else {
                            LOG.warn("Sending CommitHead failed for all sidecars");
                            isActive.set(false);
                            activeTransports.clear();
                            pendingTxRequests.clear();
                            span.setAttribute("active_transport_target", "none");
                        }
                    }
                    finally {
                        span.end();
                    }
            }).join();
            return CompletableFuture.completedFuture(null);
        }
    }

    private SendEventsRequest assembleNewIterationRequest(NewIteration iteration) {
        var sendEvents = new SendEventsRequest();

        if (maybeNewHead.isPresent()) {
            var newHead = new CommitHeadReqItem(maybeNewHead.get());
            sendEvents.getEvents().addLast(newHead);
        }

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

            // Subscribe to results stream for each transport
            for (ISidecarTransport transport : successfulTransports) {
                transport.subscribeResults(
                    this::onTransactionResult,
                    error -> {
                        LOG.error("Results subscription error: {}", error.getMessage());
                        activeTransports.remove(transport);
                    }
                );
            }
        } finally {
            span.end();
        }
    }

    /**
     * Handle incoming transaction result from the SubscribeResults stream.
     * Matches the result to a pending request and completes its future.
     */
    private void onTransactionResult(TransactionResult result) {
        TxExecutionId txExecId = result.getTxExecutionId();
        CompletableFuture<GetTransactionResponse> future = pendingTxRequests.get(txExecId);

        if (future != null) {
            LOG.debug("Received result for pending tx: hash={}, status={}",
                txExecId.getTxHash(), result.getStatus());
            future.complete(new GetTransactionResponse(result));
        } else {
            LOG.trace("Received result for unknown/already-completed tx: hash={}",
                txExecId.getTxHash());
        }
    }
    
    private CompletableFuture<TransportResponse> sendIterationToTransport(SendEventsRequest events, ISidecarTransport transport) {
        long startTime = System.currentTimeMillis();
        metricsRegistry.getSidecarRpcCounter().labels(CredibleLayerMethods.SEND_EVENTS).inc();
        return transport.sendEvents(events)
            .thenApply(sendEventsResponse -> {
                long latency = System.currentTimeMillis() - startTime;
                return new TransportResponse(transport, "accepted".equals(sendEventsResponse.getStatus()), "Success", latency);
            })
            .exceptionally(ex -> {
                LOG.debug("NewIteration error: {} - {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getMessage() : "");
                metricsRegistry.getErrorCounter().labels().inc();
                activeTransports.remove(transport);
                long latency = System.currentTimeMillis() - startTime;
                return new TransportResponse(transport, false, ex.getMessage(), latency);
            });
    }
    
    @Override
    public List<CompletableFuture<GetTransactionResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest) {
        var span = tracer.spanBuilder("dispatchTransactions").startSpan();
        try(Scope scope = span.makeCurrent()) {
            if (activeTransports.isEmpty()) {
                LOG.warn("Active sidecars empty");
                span.setAttribute("failed", true);
                span.end();
                return Collections.emptyList();
            }

            List<TxExecutionId> txExecutionIds = sendTxRequest.getTransactions().stream()
                .map(tx -> tx.getTxExecutionId())
                .collect(Collectors.toList());

            Context context = Context.current();

            // Send transactions to all active transports
            for (ISidecarTransport transport : activeTransports) {
                metricsRegistry.getSidecarRpcCounter().labels(CredibleLayerMethods.SEND_TRANSACTIONS).inc();
                var sendTxSpan = tracer.spanBuilder(CredibleLayerMethods.SEND_TRANSACTIONS).startSpan();
                transport.sendTransactions(sendTxRequest)
                    .whenComplete((result, ex) -> {
                        try(Scope sendScope = context.makeCurrent()) {
                            if (ex != null) {
                                LOG.debug("SendTransactions error: {} - {}",
                                    ex.getMessage(),
                                    ex.getCause() != null ? ex.getCause().getMessage() : "");
                                metricsRegistry.getErrorCounter().labels().inc();
                                activeTransports.remove(transport);
                                sendTxSpan.setAttribute("failed", true);
                            } else {
                                LOG.debug("SendTransactions response: count - {}, message - {}",
                                    result.getRequestCount(),
                                    result.getMessage());
                                sendTxSpan.setAttribute("message", result.getMessage());
                            }
                        } finally {
                            sendTxSpan.end();
                        }
                    });
            }

            if (!isActive.get()) {
                LOG.debug("Transports aren't active!");
                span.end();
                return Collections.emptyList();
            }

            // Create a future that will be completed when the result arrives via SubscribeResults stream
            // NOTE: making the assumption that it's only 1 transaction per request
            // which is implied with the TransactionSelectionPlugin
            TxExecutionId txExecId = txExecutionIds.get(0);
            CompletableFuture<GetTransactionResponse> resultFuture = new CompletableFuture<>();
            pendingTxRequests.put(txExecId, resultFuture);

            LOG.debug("Dispatched transaction, waiting for result via stream: hash={}", txExecId.getTxHash());
            span.end();

            // Return a single-element list for compatibility with existing interface
            return Collections.singletonList(resultFuture);
        }
    }
    
    @Override
    public Result<GetTransactionResponse, CredibleRejectionReason> getTransactionResult(GetTransactionRequest transactionRequest) {
        if (!isActive.get()) {
            return Result.failure(CredibleRejectionReason.NO_ACTIVE_TRANSPORT);
        }

        TxExecutionId txExecId = transactionRequest.toTxExecutionId();
        CompletableFuture<GetTransactionResponse> future = pendingTxRequests.get(txExecId);
        if (future == null) {
            LOG.debug("No pending request found for transaction {}", txExecId);
            return Result.failure(CredibleRejectionReason.NO_RESULT);
        }

        try {
            // Wait for result with timeout - result will be completed by onTransactionResult callback
            GetTransactionResponse response = future
                .orTimeout(processingTimeout, TimeUnit.MILLISECONDS)
                .get();
            return Result.success(response);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                LOG.debug("Timeout waiting for transaction result: {}", txExecId.getTxHash());
                metricsRegistry.getTimeoutCounter().labels().inc();
                // Mark strategy as inactive when results timeout - no sidecar responded in time
                isActive.set(false);
                return Result.failure(CredibleRejectionReason.TIMEOUT);
            }
            LOG.debug("Exception waiting for transaction result: {}, cause: {}",
                e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "null");
            metricsRegistry.getErrorCounter().labels().inc();
            return Result.failure(CredibleRejectionReason.ERROR);
        } catch (InterruptedException e) {
            LOG.debug("Interrupted waiting for transaction result: {}", txExecId.getTxHash());
            metricsRegistry.getErrorCounter().labels().inc();
            Thread.currentThread().interrupt();
            return Result.failure(CredibleRejectionReason.ERROR);
        } finally {
            pendingTxRequests.remove(txExecId);
        }
    }

    @Override
    public CompletableFuture<List<ReorgResponse>> sendReorgRequest(ReorgRequest reorgRequest) {
        List<CompletableFuture<ReorgResponse>> reorgFutures = activeTransports.stream()
            .map(transport -> {
                metricsRegistry.getReorgRequestCounter().labels().inc();
                return transport.sendReorg(reorgRequest)
                    .exceptionally(ex -> {
                        // Safe to remove with CopyOnWriteArrayList
                        activeTransports.remove(transport);
                        LOG.debug("Exception sending reorg request to transport {}: {}",
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
    public void setNewHead(String blockhash, CommitHead newHead) {
        var iterationId = blockHashToIterationId.get(blockhash);
        if (iterationId == null) {
            LOG.warn("No iteration id found for blockhash {}", blockhash);
        }
        blockHashToIterationId.clear();
        newHead.setSelectedIterationId(iterationId);
        maybeNewHead = Optional.of(newHead);
    }

    @Override
    public void endIteration(String blockhash, Long iterationId) {
        blockHashToIterationId.putIfAbsent(blockhash, iterationId);
    }
}
