package net.phylax.credible.txselection;

import java.util.List;

import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;
import org.slf4j.Logger;

import net.phylax.credible.metrics.CredibleMetricsRegistry;
import net.phylax.credible.strategy.ISidecarStrategy;
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

  public CredibleTransactionSelector(final Config config, final CredibleMetricsRegistry metricsRegistry) {
    this.config = config;
    this.metricsRegistry = metricsRegistry;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext txContext) {
    var timing = metricsRegistry.getPreProcessingTimer().labels().startTimer();
    metricsRegistry.getTransactionCounter().labels().inc();

    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        LOG.debug("Sending transaction {} for processing ", txHash);

        // Create request with proper models
        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(List.of(new TransactionWithHash(txEnv, txHash)));

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

    if (!config.strategy.isActive()) {
      LOG.warn("No active tranpsport available!");
      return TransactionSelectionResult.SELECTED;
    }

    try {
        LOG.debug("Awaiting result for {}", txHash);

        var txResponseResult = config.strategy.getTransactionResult(txHash);

        if (!txResponseResult.isSuccess()) {
          LOG.warn("Credible Layer failed to process tx {}, reason: {}", txHash, txResponseResult.getFailure());
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

    try {
        LOG.debug("Sending reorg request for transaction {} due to: {}", txHash, reason);

        // Create reorg request with the transaction hash
        ReorgRequest reorgRequest = new ReorgRequest(txHash);
        var reorgResponses = config.strategy.sendReorgRequest(reorgRequest);

        LOG.debug("Reorg request successful for transaction {}, got {} responses", txHash, reorgResponses.size());
    } catch (Exception e) {
        LOG.error("Failed to send reorg request for transaction {}: {}", txHash, e.getMessage(), e);
    }
  }
}