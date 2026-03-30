package net.phylax.credible.strategy;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.phylax.credible.types.CredibleRejectionReason;
import net.phylax.credible.types.SidecarApiModels.*;
import net.phylax.credible.utils.Result;

public interface ISidecarStrategy {
    /**
     * Sends a CommitHead to all transports and waits for acknowledgment.
     * This method blocks until at least one transport acknowledges the commit head
     * or all transports fail/timeout.
     *
     * @param commitHead the CommitHead to send
     * @param timeoutMs maximum time to wait for acknowledgment in milliseconds
     * @return true if at least one transport acknowledged successfully, false otherwise
     */
    boolean commitHead(CommitHead commitHead, long timeoutMs);

    /**
     * This method should be called when all transactions for an iteration are dispatched. It's marking
     * the end of an iteration inside of the block building process.
     * @param blockHash
     * @param iterationId
     */
    void endIteration(String blockHash, Long iterationId);

    /**
     * Handles sending the block env to the sidecar.
     * 
     * @param blockEnvRequest BlockEnvRequest instance
     * @return CompletableFuture that completes when the request is processed
     */
    CompletableFuture<Void> newIteration(NewIteration newIteration);

    /**
     * Sends transactions for processing and registers per-transaction result futures.
     * The returned futures are completed by {@code SubscribeResults} first and may later
     * be completed by unary {@code GetTransaction} recovery lookups if the initial stream
     * wait slice expires. Dispatch itself is non-blocking and does not treat a missing
     * unary result as terminal.
     *
     * @param sendTxRequest SendTransactionsRequest instance
     * @return List of CompletableFutures, one future per dispatched transaction
     */
    List<CompletableFuture<GetTransactionResponse>> dispatchTransactions(
        SendTransactionsRequest sendTxRequest);

    /**
     * Resolves a dispatched transaction result within the configured processing window.
     * The strategy first waits for the tracked stream result, then starts unary
     * {@code GetTransaction} lookups against active transports without removing the
     * pending request. Unary {@code not_found} responses are non-terminal; the first
     * concrete result from either source wins. {@code PROCESSING_TIMEOUT} is returned
     * only after the full processing window is exhausted without any concrete result.
     *
     * @param transactionRequest GetTransactionRequest containing the transaction execution ID
     * @return Result<GetTransactionResponse, CredibleRejectionReason> containing either a
     * concrete transaction result or the rejection reason
     */
    Result<GetTransactionResponse, CredibleRejectionReason> getTransactionResult(GetTransactionRequest transactionRequest);

    /**
     * Send the reorg request to the sidecar.
     *
     * @param txExecId TxExecutionId containing the block number, iteration ID and transaction hash to reorg
     * @return List of ReorgResponses, one from each instance of the transport
     */
    CompletableFuture<List<ReorgResponse>> sendReorgRequest(ReorgRequest reorgRequest);

    /**
     * Determines if the strategy is active or not, i.e. are the sidecars available and responding.
     *
     * @return
     */
    boolean isActive();

    /**
     * Sets the active state of the strategy
     * @param active
     */
    void setActive(boolean active);
}
