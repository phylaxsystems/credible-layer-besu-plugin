package net.phylax.credible.strategy;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels.*;

public class DefaultSidecarStrategy implements ISidecarStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSidecarStrategy.class);

    private List<ISidecarTransport> transports = new ArrayList<>();
    private List<ISidecarTransport> activeTransports = new CopyOnWriteArrayList<>();
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
        
        // Fire and forget sendTransactions to all active transports
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

        List<CompletableFuture<GetTransactionsResponse>> futures = activeTransports.stream()
            .map(transport -> getTransactionsFromTransport(transport, hashes))
            .collect(Collectors.toList());
        
        return futures;
    }
    
    private CompletableFuture<GetTransactionsResponse> getTransactionsFromTransport(ISidecarTransport transport, List<String> txHashes) {
        long startTime = System.currentTimeMillis();
        return transport.getTransactions(txHashes)
            .thenApply(result -> {
                long latency = System.currentTimeMillis() - startTime;
                return result;
            })
            .orTimeout(processingTimeout, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                // TODO: what to do with the transport?
                long latency = System.currentTimeMillis() - startTime;
                LOG.debug("Timeout or error getting transactions: latency {} -{}", 
                    latency, ex.getMessage());
                return null;
            });
    }
    
    @Override
    public List<TransactionResult> handleTransportResponses(List<CompletableFuture<GetTransactionsResponse>> futures) {
        if (futures.isEmpty()) {
            return Collections.emptyList();
        }
        
        CompletableFuture<GetTransactionsResponse> anySuccess = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(result -> {
                if (result instanceof GetTransactionsResponse) {
                    return (GetTransactionsResponse) result;
                }
                return null;
            });
        
        try {
            GetTransactionsResponse response = anySuccess.get();
            
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                LOG.debug("No valid results from sidecar");
                return Collections.emptyList();
            }
            
            LOG.debug("GetTransactionsResponse successfully handled: {}", response.getResults().get(0).getHash());
            return response.getResults();
        } catch (InterruptedException | ExecutionException e) {
            LOG.debug("Exception waiting for sidecar responses: {}", e.getMessage());
            return Collections.emptyList();
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
                LOG.debug("Failed to send reorg request to transport {}: {}", 
                    transport.toString(), e.getMessage());
            }
        }
        
        return successfulResponses;
    }
}