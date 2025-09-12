package net.phylax.credible.transport;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.phylax.credible.types.SidecarApiModels.*;

public interface ISidecarTransport {
    CompletableFuture<SendBlockEnvResponse> sendBlockEnv(SendBlockEnvRequest blockEnv);
    CompletableFuture<SendTransactionsResponse> sendTransactions(SendTransactionsRequest transactions);
    CompletableFuture<GetTransactionsResponse> getTransactions(List<String> txHashes);
}