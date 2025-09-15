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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels.GetTransactionsResponse;
import net.phylax.credible.types.SidecarApiModels.ReorgRequest;
import net.phylax.credible.types.SidecarApiModels.ReorgResponse;
import net.phylax.credible.types.SidecarApiModels.SendBlockEnvRequest;
import net.phylax.credible.types.SidecarApiModels.SendTransactionsRequest;
import net.phylax.credible.types.SidecarApiModels.TransactionResult;

public class DefaultSidecarStrategy implements ISidecarStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSidecarStrategy.class);

    private List<ISidecarTransport> transports = new ArrayList<>();
    private List<ISidecarTransport> activeTransports = new CopyOnWriteArrayList<>();
    private final Map<String, List<CompletableFuture<GetTransactionsResponse>>> pendingTxRequests = 
        new ConcurrentHashMap<>();
    private int processingTimeout;

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

    public DefaultSidecarStrategy(List<ISidecarTransport> transports, int processingTimeout) {
        this.transports = transports;
        this.activeTransports = new CopyOnWriteArrayList<>();
        this.processingTimeout = processingTimeout;
    }
    
    @Override
    public CompletableFuture<Void> sendBlockEnv(SendBlockEnvRequest blockEnv) {
        List<CompletableFuture<TransportResponse>> futures = transports.stream()
            .map(transport -> sendBlockEnvToTransport(blockEnv, transport))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenAccept(voidResult -> {
                // Filter successful transports
                List<ISidecarTransport> successfulTransports = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(TransportResponse::isSuccess)
                    .map(TransportResponse::getTransport)
                    .collect(Collectors.toList());
                
                // Clear pending requests (since it's the start of the new block)
                pendingTxRequests.clear();
                // Update active transports list
                activeTransports.clear();
                activeTransports.addAll(successfulTransports);
                LOG.debug("Updated active sidecars - count {}", successfulTransports.size());
            });
    }
    
    private CompletableFuture<TransportResponse> sendBlockEnvToTransport(SendBlockEnvRequest blockEnv, ISidecarTransport transport) {
        long startTime = System.currentTimeMillis();
        
        return transport.sendBlockEnv(blockEnv)
            .thenApply(voidResult -> {
                long latency = System.currentTimeMillis() - startTime;
                return new TransportResponse(transport, true, "Success", latency);
            })
            .exceptionally(ex -> {
                LOG.debug("SendBlockEnv error: {} - {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getMessage() : "");
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
            transport.sendTransactions(sendTxRequest).whenComplete((result, ex) -> {
                if (ex != null) {
                    // TODO: what to do with the transport?
                    LOG.debug("SendTransactions error: {} - {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getMessage() : "");
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
            .map(transport -> transport.getTransactions(hashes))
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
            } catch (Exception e) {
                // TODO: what to do with the transport?
                LOG.debug("Exception sending reorg request to transport {}: {}", 
                    transport.toString(), e.getMessage());
            }
        }
        
        return successfulResponses;
    }
}