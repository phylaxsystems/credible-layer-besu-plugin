package net.phylax.credible.strategy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels.*;

public class DefaultSidecarStrategy implements ISidecarStrategy {
    private List<ISidecarTransport> activeTransports = new CopyOnWriteArrayList<>();

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

    public DefaultSidecarStrategy() {}
    
    @Override
    public CompletableFuture<Void> sendBlockEnv(SendBlockEnvRequest blockEnv, List<ISidecarTransport> transports) {
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
            });
    }
    
    private CompletableFuture<TransportResponse> sendBlockEnvToTransport(SendBlockEnvRequest blockEnv, ISidecarTransport transport) {
        long startTime = System.currentTimeMillis();
        
        try {
            return transport.sendBlockEnv(blockEnv)
                .thenApply(voidResult -> {
                    long latency = System.currentTimeMillis() - startTime;
                    return new TransportResponse(transport, true, "Success", latency);
                })
                .exceptionally(ex -> {
                    // TODO: log
                    long latency = System.currentTimeMillis() - startTime;
                    return new TransportResponse(transport, false, ex.getMessage(), latency);
                });
        } catch (ISidecarTransport.TransportException e) {
            // TODO: log
            long latency = System.currentTimeMillis() - startTime;
            return CompletableFuture.completedFuture(
                new TransportResponse(transport, false, e.getMessage(), latency)
            );
        }
    }
    
    @Override
    public List<CompletableFuture<GetTransactionsResponse>> dispatchTransactions(
            SendTransactionsRequest sendTxRequest, List<ISidecarTransport> activeTransports) {
        
        if (activeTransports.isEmpty()) {
            // TODO: log empty active sidecars
            return Collections.emptyList();
        }
        
        // Fire and forget sendTransactions to all active transports
        activeTransports.forEach(transport -> {
            try {
                // TODO: retry logic
                transport.sendTransactions(sendTxRequest)
                .exceptionally(ex -> {
                    // TODO: LOG ERROR
                    return null;
                });
            } catch(ISidecarTransport.TransportException ex) {
                // TODO: log
                // TODO: should remove from active?
            }
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
        try {
            return transport.getTransactions(txHashes)
                .thenApply(result -> {
                    long latency = System.currentTimeMillis() - startTime;
                    return result;
                })
                .exceptionally(ex -> {
                    // TODO: log
                    long latency = System.currentTimeMillis() - startTime;
                    return null;
                });
        } catch (ISidecarTransport.TransportException e) {
            // TODO: log
            long latency = System.currentTimeMillis() - startTime;
            return CompletableFuture.completedFuture(null);
        }
    }
    
    @Override
    public void handleTransportResponses(List<CompletableFuture<GetTransactionsResponse>> futures, 
        List<ISidecarTransport> activeTransports) {

        try {
            CompletableFuture<Object> firstSuccessFuture = CompletableFuture.anyOf(
                futures.toArray(new CompletableFuture[0])
            );

            // Wait for first response (success or failure)
            // TODO: use proper config timeout
            Object firstResult = firstSuccessFuture.get(1, TimeUnit.SECONDS);
            
            if (firstResult instanceof GetTransactionsResponse) {
                GetTransactionsResponse response = (GetTransactionsResponse) firstResult;
                // TODO: process response
            } else {
                // TODO: log
            }
            
        } catch (TimeoutException e) {
            System.err.println("Timeout waiting for first response");
        } catch (Exception e) {
            System.err.println("Error waiting for first response: " + e.getMessage());
        }
    }
}