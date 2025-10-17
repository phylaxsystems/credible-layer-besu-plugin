package net.phylax.credible.strategy;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.phylax.credible.types.CredibleRejectionReason;
import net.phylax.credible.types.SidecarApiModels.*;
import net.phylax.credible.utils.Result;

public interface ISidecarStrategy {
    /**
     * Handles sending the block env to the sidecar.
     * 
     * @param blockEnvRequest BlockEnvRequest instance
     * @return CompletableFuture that completes when the request is processed
     */
    CompletableFuture<Void> sendBlockEnv(SendBlockEnvRequest blockEnvRequest);

    /**
     * Send the transactions for processing to the sidecar and starts the long polling
     * of dispatched hashes. Returns a List of CompletableFutures for one future per
     * transport (as multiple transports may be used).
     * 
     * @param sendTxRequest SendTransactionsRequest instance
     * @return List of CompletableFutures, one future from a single transport
     */
    List<CompletableFuture<GetTransactionsResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest);

    /**
     * Get the results of the transactions from the sidecar. This method is called
     * after the dispatchTransactions method and the futures should resolve inside of it.
     * 
     * @param txHashes List of transaction hashes
     * @return Result<GetTransactionsResponse, CredibleRejectionReason> containing the results of the sidecar processing
     * or the reason it got rejected
     */
    Result<GetTransactionsResponse, CredibleRejectionReason> getTransactionResults(List<String> txHashes);

    /**
     * Send the reorg request to the sidecar.
     * 
     * @param reorgRequest ReorgRequest instance
     * @return List of ReorgResponses, one from each instance of the transport
     */
    List<ReorgResponse> sendReorgRequest(ReorgRequest reorgRequest);

    /**
     * Determines if the strategy is active or not, i.e. are the sidecars available and responding.
     *
     * @return
     */
    boolean isActive();
}