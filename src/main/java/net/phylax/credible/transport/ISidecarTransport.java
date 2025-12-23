package net.phylax.credible.transport;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.phylax.credible.types.SidecarApiModels.*;

public interface ISidecarTransport {
    CompletableFuture<SendTransactionsResponse> sendTransactions(SendTransactionsRequest transactions);
    CompletableFuture<GetTransactionsResponse> getTransactions(GetTransactionsRequest transactions);
    CompletableFuture<GetTransactionResponse> getTransaction(GetTransactionRequest transactions);
    CompletableFuture<ReorgResponse> sendReorg(ReorgRequest reorgRequest);
    CompletableFuture<SendEventsResponse> sendEvents(SendEventsRequest events);
    CompletableFuture<Boolean> sendEvent(SendEventsRequestItem event);

    /**
     * Subscribe to transaction results stream.
     * Results are pushed by the server as transactions complete execution.
     *
     * @param onResult callback invoked for each received TransactionResult
     * @param onError callback invoked on stream error
     * @return CompletableFuture that completes when the stream is established
     */
    default CompletableFuture<Void> subscribeResults(Consumer<TransactionResult> onResult, Consumer<Throwable> onError) {
        // Default no-op implementation for backwards compatibility
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Close the results subscription stream if open.
     */
    default void closeResultsSubscription() {
        // Default no-op implementation
    }
}