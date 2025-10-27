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
    List<CompletableFuture<GetTransactionResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest);

    /**
     * Get the result processing a transaction in the credible layer. This method is called
     * after the dispatchTransactions method and the futures should resolve inside of it.
     * 
     * @param txExecId TxExecutionId containing the block number, hash and the iteration ID
     * @return Result<GetTransactionResponse, CredibleRejectionReason> Contains either the result of the transaction processing 
     * or the reason it got rejected
     */
    Result<GetTransactionResponse, CredibleRejectionReason> getTransactionResult(TxExecutionId txExecId);

    /**
     * Send the reorg request to the sidecar.
     *
     * @param txExecId TxExecutionId containing the block number, iteration ID and transaction hash to reorg
     * @return List of ReorgResponses, one from each instance of the transport
     */
    List<ReorgResponse> sendReorgRequest(TxExecutionId txExecId);

    /**
     * Determines if the strategy is active or not, i.e. are the sidecars available and responding.
     *
     * @return
     */
    boolean isActive();
}