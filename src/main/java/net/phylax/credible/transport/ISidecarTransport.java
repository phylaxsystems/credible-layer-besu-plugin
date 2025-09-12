package net.phylax.credible.transport;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.phylax.credible.types.SidecarApiModels.*;

public interface ISidecarTransport {
    CompletableFuture<SendBlockEnvResponse> sendBlockEnv(SendBlockEnvRequest blockEnv) throws TransportException;
    CompletableFuture<SendTransactionsResponse> sendTransactions(SendTransactionsRequest transactions) throws TransportException;
    CompletableFuture<GetTransactionsResponse> getTransactions(List<String> txHashes) throws TransportException;


    public static class TransportException extends Exception {
        private final Exception error;
        
        public TransportException(Exception error) {
            super(error.getMessage());
            this.error = error;
        }
        
        public TransportException(String message) {
            super(message);
            this.error = null;
        }
        
        public TransportException(String message, Throwable cause) {
            super(message, cause);
            this.error = null;
        }
    }
}