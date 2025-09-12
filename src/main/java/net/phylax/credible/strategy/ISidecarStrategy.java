package net.phylax.credible.strategy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.phylax.credible.transport.ISidecarTransport;
import net.phylax.credible.types.SidecarApiModels.*;

public interface ISidecarStrategy {
    CompletableFuture<Void> sendBlockEnv(SendBlockEnvRequest blockEnvRequest, List<ISidecarTransport> transports);

    List<CompletableFuture<GetTransactionsResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest, List<ISidecarTransport> activeTransports);

    void handleTransportResponses(List<CompletableFuture<GetTransactionsResponse>> futures, 
        List<ISidecarTransport> activeTransports);
}