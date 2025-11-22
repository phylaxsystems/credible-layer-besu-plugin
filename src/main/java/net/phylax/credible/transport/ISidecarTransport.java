package net.phylax.credible.transport;

import java.util.concurrent.CompletableFuture;

import net.phylax.credible.types.SidecarApiModels.*;

public interface ISidecarTransport {
    CompletableFuture<SendTransactionsResponse> sendTransactions(SendTransactionsRequest transactions);
    CompletableFuture<GetTransactionsResponse> getTransactions(GetTransactionsRequest transactions);
    CompletableFuture<GetTransactionResponse> getTransaction(GetTransactionRequest transactions);
    CompletableFuture<ReorgResponse> sendReorg(ReorgRequest reorgRequest);
    CompletableFuture<SendEventsResponse> sendEvents(SendEventsRequest events);
}