package net.phylax.credible.txselection;

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
    var timing = metricsRegistry.getPreProcessingTimer().labels().startTimer();
    metricsRegistry.getTransactionCounter().labels().inc();

    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();
    long blockNumber = txContext.getPendingBlockHeader().getNumber();
    long iterationId = getOperationTracer().getCurrentIterationId();

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        LOG.debug("Sending transaction for processing, hash: {}, iteration: {}", txHash, iterationId);

        TxExecutionId txExecutionId = new TxExecutionId(blockNumber, iterationId, txHash);
        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(List.of(new TransactionExecutionPayload(txExecutionId, txEnv)));

        config.strategy.dispatchTransactions(sendRequest);

        LOG.debug("Started async transaction processing for {}", txHash);
    } catch (Exception e) {
        LOG.error("Error in transaction preprocessing for {}: {}", txHash, e.getMessage());
    } finally {
        timing.stopTimer();
    }
    
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext txContext,
      final TransactionProcessingResult transactionProcessingResult) {
    var timing = metricsRegistry.getPostProcessingTimer().labels().startTimer();

    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();
    long blockNumber = txContext.getPendingBlockHeader().getNumber();
    long iterationId = getOperationTracer().getCurrentIterationId();

    if (!config.strategy.isActive()) {
      LOG.warn("No active transport available!");
      return TransactionSelectionResult.SELECTED;
    }

    try {
        LOG.debug("Awaiting result for, hash: {}, iteration: {}", txHash, iterationId);

        GetTransactionRequest txRequest = new GetTransactionRequest(blockNumber, iterationId, txHash);

        var txResponseResult = config.strategy.getTransactionResult(txRequest);

        if (!txResponseResult.isSuccess()) {
          LOG.warn("Credible Layer failed to process, tx: {}, iteration: {}, reason: {}", txHash, iterationId, txResponseResult.getFailure());
          return TransactionSelectionResult.SELECTED;
        }

        var txResult = txResponseResult.getSuccess().getResult();

        var status = txResult.getStatus();
        if (TransactionStatus.ASSERTION_FAILED.equals(status) || 
              TransactionStatus.FAILED.equals(status)) {
              LOG.info("Transaction {} excluded due to status: {}", txHash, status);
              metricsRegistry.getInvalidationCounter().labels().inc();
              return TransactionSelectionResult.invalid("TX rejected by Credible layer");
          } else {
              LOG.debug("Transaction {} included with status: {}", txHash, status);
              return TransactionSelectionResult.SELECTED;
          }
    } catch (Exception e) {
        LOG.error("Error in transaction postprocessing for {}: {}", txHash, e.getMessage());
        return TransactionSelectionResult.SELECTED;
    } finally {
        timing.stopTimer();
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
    long iterationId = getOperationTracer().getCurrentIterationId();

    try {
        LOG.debug("Sending reorg request for transaction {} due to: {}", txHash, reason);

        // Create TxExecutionId with block number, iteration ID, and transaction hash
        ReorgRequest reorgRequest = new ReorgRequest(blockNumber, iterationId, txHash);
        var reorgResponses = config.strategy.sendReorgRequest(reorgRequest);

        LOG.debug("Reorg request successful for transaction {}, got {} responses", txHash, reorgResponses.size());
    } catch (Exception e) {
        LOG.error("Failed to send reorg request for transaction {}: {}", txHash, e.getMessage(), e);
    }
  }

  public Long getIterationId() {
    return iterationId;
  }
}