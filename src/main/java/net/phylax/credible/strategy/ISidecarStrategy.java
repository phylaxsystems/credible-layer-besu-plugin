package net.phylax.credible.strategy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels.*;

public interface ISidecarStrategy {
    CompletableFuture<Void> sendBlockEnv(SendBlockEnvRequest blockEnvRequest);

    List<CompletableFuture<GetTransactionsResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest);

    List<TransactionResult> handleTransportResponses(List<CompletableFuture<GetTransactionsResponse>> futures);
}