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
    private ISidecarStrategy strategy;

    public Config(ISidecarStrategy strategy) {
      this.strategy = strategy;
    }

    public ISidecarStrategy getStrategy() {
      return strategy;
    }
  }

  private final Config config;
  private final CredibleMetricsRegistry metricsRegistry;
  private final Long iterationId;
  private String transactionHash;
  private List<TxExecutionId> transactions = new ArrayList<>();

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

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        log.debug("Sending transaction for processing, hash: {}, iteration: {}, prevTxHash: {}, index: {}", transactionHash, iterationId, ByteUtils.toHex(prevTxHash), txIndex);

        TxExecutionId txExecutionId = new TxExecutionId(blockNumber, iterationId, txHashBytes, txIndex);
        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(Collections.singletonList(new TransactionExecutionPayload(txExecutionId, txEnv, prevTxHash)));

        config.strategy.dispatchTransactions(sendRequest);
        transactions.add(txExecutionId);

        log.debug("Started async transaction processing for {}", transactionHash);
    } catch (Exception e) {
        log.error("Error in transaction preprocessing for {}: {}", transactionHash, e.getMessage());
        status = "error";
    } finally {
        metricsRegistry.getPreProcessingDuration().labels(status).observe(getDurationSeconds(startTime));
    }

    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext txContext,
      final TransactionProcessingResult transactionProcessingResult) {
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

    // If we didn't process the tx, nothing to do
    byte[] lastTxHash = getLastTxHash();
    if (lastTxHash == null || !java.util.Arrays.equals(txHashBytes, lastTxHash)) {
      log.debug("Last tx hash mismatch. Skipping reorg for {}, reason: {}", txHashHex, reason);
      return;
    }

    transactions.remove(transactions.size() - 1);
    long index = transactions.size();

    try {
        log.debug("Sending reorg request for transaction {} due to: {}", txHashHex, reason);

        // Create TxExecutionId with block number, iteration ID, and transaction hash (as byte[])
        ReorgRequest reorgRequest = new ReorgRequest(blockNumber, iterationId, txHashBytes, index);
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