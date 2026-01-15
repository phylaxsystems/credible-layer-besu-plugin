package net.phylax.credible.txselection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.strategy.ISidecarStrategy;
import net.phylax.credible.tracer.CredibleOperationTracer;
import net.phylax.credible.types.SidecarApiModels.GetTransactionRequest;
import net.phylax.credible.types.SidecarApiModels.ReorgRequest;
import net.phylax.credible.types.SidecarApiModels.SendTransactionsRequest;
import net.phylax.credible.types.SidecarApiModels.TransactionExecutionPayload;
import net.phylax.credible.types.SidecarApiModels.TransactionStatus;
import net.phylax.credible.types.SidecarApiModels.TxEnv;
import net.phylax.credible.types.SidecarApiModels.TxExecutionId;
import net.phylax.credible.types.TransactionConverter;
import net.phylax.credible.utils.ByteUtils;

@Slf4j
public class CredibleTransactionSelector implements PluginTransactionSelector {
  public static class Config {
    private final ISidecarStrategy strategy;
    private final int aggreagatedTimeoutMs;

    public Config(ISidecarStrategy strategy, int aggreagatedTimeoutMs) {
      this.strategy = strategy;
      this.aggreagatedTimeoutMs = aggreagatedTimeoutMs;
    }

    public ISidecarStrategy getStrategy() {
      return strategy;
    }

    public int getAggregatedTimeoutMs() {
      return aggreagatedTimeoutMs;
    }
  }

  private final Config config;
  private final CredibleMetricsRegistry metricsRegistry;
  private final Long iterationId;
  private boolean iterationTimedOut = false;
  private String transactionHash;
  private List<TxExecutionId> transactions = new ArrayList<>();
  // Aggregated time for pre and post processing within a single iteration
  private long aggregatedTimeExecutionMicros = 0;
  // Keeps track of the transactions in a bundle that have been selected but rollbacked
  private long rollbackBundledTxCounter = 0;

  public CredibleTransactionSelector(
    final Config config,
    final Long iterationId,
    final CredibleMetricsRegistry metricsRegistry) {
    this.config = config;
    this.iterationId = iterationId;
    this.metricsRegistry = metricsRegistry;
  }

  @Override
  public CredibleOperationTracer getOperationTracer() {
    return new CredibleOperationTracer(this.iterationId, this.config.getStrategy());
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext txContext) {
    long startTime = System.nanoTime();
    String status = "success";
    metricsRegistry.getTransactionCounter().labels().inc();

    var tx = txContext.getPendingTransaction().getTransaction();
    // Store hash as byte[] for efficiency
    byte[] txHashBytes = tx.getHash().toArrayUnsafe();
    transactionHash = tx.getHash().toHexString(); // Keep for logging
    long blockNumber = txContext.getPendingBlockHeader().getNumber();
    long iterationId = getOperationTracer().getCurrentIterationId();
    long txIndex = transactions.size();
    byte[] prevTxHash = getLastTxHash();

    // Assemble the TxExecutionId
    TxExecutionId txExecutionId = new TxExecutionId(blockNumber, iterationId, txHashBytes, txIndex);

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        log.debug("Sending transaction for processing, hash: {}, iteration: {}, prevTxHash: {}, index: {}", transactionHash, iterationId, ByteUtils.toHex(prevTxHash), txIndex);

        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(Collections.singletonList(new TransactionExecutionPayload(txExecutionId, txEnv, prevTxHash)));

        config.strategy.dispatchTransactions(sendRequest);
        
        log.debug("Started async transaction processing for {}", transactionHash);
    } catch (Exception e) {
        log.error("Error in transaction preprocessing for {}: {}", transactionHash, e.getMessage());
        status = "error";
    } finally {
        // We still add the tx execution to the internal list even if there was an error
        // If the tx wasn't sent or some internal error happened, the strategy will handle the error
        transactions.add(txExecutionId);
        metricsRegistry.getPreProcessingDuration().labels(status).observe(getDurationSeconds(startTime));
        aggregatedTimeExecutionMicros += getDurationMicros(startTime);
    }

    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext txContext,
      final TransactionProcessingResult transactionProcessingResult) {
    checkAggregatedTimeout();

    if (!config.strategy.isActive()) {
      log.warn("No active transport available!");
      return TransactionSelectionResult.SELECTED;
    }

    long startTime = System.nanoTime();
    String status = "success";

    long blockNumber = txContext.getPendingBlockHeader().getNumber();
    long iterationId = getOperationTracer().getCurrentIterationId();

    long index = transactions.size() - 1;

    try {
        log.debug("Awaiting result for, hash: {}, iteration: {}, index: {}", transactionHash, iterationId, index);

        // Use byte[] for txHash
        byte[] txHashBytes = txContext.getPendingTransaction().getTransaction().getHash().toArrayUnsafe();
        GetTransactionRequest txRequest = new GetTransactionRequest(blockNumber, iterationId, txHashBytes, index);

        var txResponseResult = config.strategy.getTransactionResult(txRequest);

        if (!txResponseResult.isSuccess()) {
          log.warn("Credible Layer failed to process, tx: {}, iteration: {}, reason: {}", transactionHash, iterationId, txResponseResult.getFailure());
          status = "error";
          return TransactionSelectionResult.SELECTED;
        }

        var txResult = txResponseResult.getSuccess().getResult();

        var txStatus = txResult.getStatus();
        if (TransactionStatus.ASSERTION_FAILED.equals(txStatus)) {
              log.info("Transaction {} excluded due to status: {}", transactionHash, txStatus);
              metricsRegistry.getInvalidationCounter().labels().inc();
              status = "rejected";
              return TransactionSelectionResult.invalid("TX rejected by Credible layer");
          } else {
              log.debug("Transaction {} included with status: {}", transactionHash, txStatus);
              return TransactionSelectionResult.SELECTED;
          }
    } catch (Exception e) {
        log.error("Error in transaction postprocessing for {}: {}", transactionHash, e.getMessage());
        status = "error";
        return TransactionSelectionResult.SELECTED;
    } finally {
        metricsRegistry.getPostProcessingDuration().labels(status).observe(getDurationSeconds(startTime));
        aggregatedTimeExecutionMicros += getDurationMicros(startTime);
    }
  }

  @Override
  public void onTransactionNotSelected(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResult transactionSelectionResult) {

    var transaction = evaluationContext.getPendingTransaction().getTransaction();
    byte[] txHashBytes = transaction.getHash().toArrayUnsafe();
    String txHashHex = transaction.getHash().toHexString(); // For logging
    String reason = transactionSelectionResult.toString();
    long blockNumber = evaluationContext.getPendingBlockHeader().getNumber();

    // Tx unselected due to rollback in a bundle
    if ("SELECTED_ROLLBACK".equals(reason)) {
      log.debug("Transaction {} is selected for rollback due to: {}", txHashHex, reason);
      rollbackBundledTxCounter += 1;
      return;
    }

    // If we didn't process the tx, nothing to do
    byte[] lastTxHash = getLastTxHash();
    if (lastTxHash == null || !java.util.Arrays.equals(txHashBytes, lastTxHash)) {
      log.debug("Last tx hash mismatch. Skipping reorg for {}, reason: {}", txHashHex, reason);
      return;
    }

    // remove last rollbackTxCounter + 1 transactions
    transactions.remove(transactions.size() - 1);
    long index = transactions.size();

    // List of tx hashes that need to be reorged
    List<byte[]> reorgTxHashes = new ArrayList<>();
    for(int i = 0; i < rollbackBundledTxCounter; i++) {
      reorgTxHashes.addFirst(transactions.removeLast().getTxHash());
    }
    
    try {
      log.debug("Sending reorg request for transaction {} due to: {}, reorgHashes: {}", txHashHex, reason, reorgTxHashes);

      // Create TxExecutionId with block number, iteration ID, and transaction hash (as byte[])
      ReorgRequest reorgRequest = new ReorgRequest(blockNumber, iterationId, txHashBytes, index, reorgTxHashes);
      config.strategy.sendReorgRequest(reorgRequest)
        .whenComplete((res, ex) -> {
          if (ex != null) {
            log.error("Failed to send reorg request for transaction {}: {}", txHashHex, ex.getMessage(), ex);
            return;
          }
          log.debug("Reorg request successful for transaction {}, got {} responses", txHashHex, res.size());
        });
    } catch (Exception e) {
        log.error("Failed to send reorg request for transaction {}: {}", txHashHex, e.getMessage(), e);
    } finally {
      rollbackBundledTxCounter = 0;
    }
  }

  public Long getIterationId() {
    return iterationId;
  }

  public long getCurrentIndex() {
    return transactions.size();
  }

  private double getDurationSeconds(long startTimeNanos) {
    return (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
  }

  private double getDurationMicros(long startTimeNanos) {
    return (System.nanoTime() - startTimeNanos) / 1_000.0;
  }

  /**
   * Checks if the iteration has exceeded the configured timeout.
   * Once timed out, the flag is cached to avoid repeated checks.
   * When timeout occurs, the strategy is set to inactive.
   *
   * @return true if iteration timeout is configured and has been exceeded
   */
  private boolean checkAggregatedTimeout() {
    if (iterationTimedOut) {
      return true;
    }

    int timeoutMs = config.getAggregatedTimeoutMs();
    if (timeoutMs <= 0) {
      return false;
    }

    if (aggregatedTimeExecutionMicros >= timeoutMs * 1_000) {
      iterationTimedOut = true;
      log.warn("Iteration {} timeout after {}ms (limit: {}ms)", iterationId, aggregatedTimeExecutionMicros / 1_000, timeoutMs);
      metricsRegistry.getIterationTimeoutCounter().labels().inc();
      config.getStrategy().setActive(false);
      return true;
    }
    return false;
  }

  /**
   * Returns the hash of the last transaction, i.e. the previously processed one.
   * If this is the first invocation (no transactions were processed yet), return null.
   */
  private byte[] getLastTxHash() {
    if (transactions.isEmpty())
      return null;

    return transactions.get(transactions.size() - 1).getTxHash();
  }
}