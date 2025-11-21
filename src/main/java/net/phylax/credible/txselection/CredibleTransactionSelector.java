package net.phylax.credible.txselection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;
import org.slf4j.Logger;

import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.strategy.ISidecarStrategy;
import net.phylax.credible.tracer.CredibleOperationTracer;
import net.phylax.credible.types.SidecarApiModels.*;
import net.phylax.credible.types.TransactionConverter;
import net.phylax.credible.utils.CredibleLogger;

public class CredibleTransactionSelector implements PluginTransactionSelector {
  private static final Logger LOG = CredibleLogger.getLogger(CredibleTransactionSelector.class);

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
    transactionHash = tx.getHash().toHexString();
    long blockNumber = txContext.getPendingBlockHeader().getNumber();
    long iterationId = getOperationTracer().getCurrentIterationId();
    long txIndex = transactions.size();
    String prevTxHash = getLastTxHash();

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        LOG.debug("Sending transaction for processing, hash: {}, iteration: {}, prevTxHash: {}, index: {}", transactionHash, iterationId, prevTxHash, txIndex);

        TxExecutionId txExecutionId = new TxExecutionId(blockNumber, iterationId, transactionHash, txIndex);
        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(Collections.singletonList(new TransactionExecutionPayload(txExecutionId, txEnv, prevTxHash)));

        config.strategy.dispatchTransactions(sendRequest);
        transactions.add(txExecutionId);

        LOG.debug("Started async transaction processing for {}", transactionHash);
    } catch (Exception e) {
        LOG.error("Error in transaction preprocessing for {}: {}", transactionHash, e.getMessage());
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
      LOG.warn("No active transport available!");
      return TransactionSelectionResult.SELECTED;
    }

    long startTime = System.nanoTime();
    String status = "success";

    long blockNumber = txContext.getPendingBlockHeader().getNumber();
    long iterationId = getOperationTracer().getCurrentIterationId();

    long index = transactions.size() - 1;

    try {
        LOG.debug("Awaiting result for, hash: {}, iteration: {}, index: {}", transactionHash, iterationId, index);

        GetTransactionRequest txRequest = new GetTransactionRequest(blockNumber, iterationId, transactionHash, index);

        var txResponseResult = config.strategy.getTransactionResult(txRequest);

        if (!txResponseResult.isSuccess()) {
          LOG.warn("Credible Layer failed to process, tx: {}, iteration: {}, reason: {}", transactionHash, iterationId, txResponseResult.getFailure());
          status = "error";
          return TransactionSelectionResult.SELECTED;
        }

        var txResult = txResponseResult.getSuccess().getResult();

        var txStatus = txResult.getStatus();
        if (TransactionStatus.ASSERTION_FAILED.equals(txStatus)) {
              LOG.info("Transaction {} excluded due to status: {}", transactionHash, txStatus);
              metricsRegistry.getInvalidationCounter().labels().inc();
              status = "rejected";
              return TransactionSelectionResult.invalid("TX rejected by Credible layer");
          } else {
              LOG.debug("Transaction {} included with status: {}", transactionHash, txStatus);
              return TransactionSelectionResult.SELECTED;
          }
    } catch (Exception e) {
        LOG.error("Error in transaction postprocessing for {}: {}", transactionHash, e.getMessage());
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
    String txHash = transaction.getHash().toHexString();
    String reason = transactionSelectionResult.toString();
    long blockNumber = evaluationContext.getPendingBlockHeader().getNumber();

    if (txHash.equals(getLastTxHash())) {
      transactions.remove(transactions.size() - 1);
    }

    long index = transactions.size() - 1;

    try {
        LOG.debug("Sending reorg request for transaction {} due to: {}", txHash, reason);

        // Create TxExecutionId with block number, iteration ID, and transaction hash
        ReorgRequest reorgRequest = new ReorgRequest(blockNumber, iterationId, txHash, index);
        config.strategy.sendReorgRequest(reorgRequest)
          .whenComplete((res, ex) -> {
            if (ex != null) {
              LOG.error("Failed to send reorg request for transaction {}: {}", txHash, ex.getMessage(), ex);
              return;
            }
            LOG.debug("Reorg request successful for transaction {}, got {} responses", txHash, res.size());
          });

        
    } catch (Exception e) {
        LOG.error("Failed to send reorg request for transaction {}: {}", txHash, e.getMessage(), e);
    }
  }

  public Long getIterationId() {
    return iterationId;
  }

  private double getDurationSeconds(long startTimeNanos) {
    return (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
  }

  /**
   * Returns the hash of the last transaction, i.e. the previously processed one. 
   * If this is the first invocation (no transactions were processed yet), return null.
   */
  private String getLastTxHash() {
    if (transactions.isEmpty())
      return null;

    return transactions.get(transactions.size() - 1).getTxHash();
  }
}