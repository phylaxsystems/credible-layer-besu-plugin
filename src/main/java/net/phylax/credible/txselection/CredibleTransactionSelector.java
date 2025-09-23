package net.phylax.credible.txselection;

import java.util.Arrays;
import java.util.List;

import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.phylax.credible.metrics.CredibleMetrics;
import net.phylax.credible.strategy.ISidecarStrategy;
import net.phylax.credible.types.SidecarApiModels.*;
import net.phylax.credible.types.TransactionConverter;

public class CredibleTransactionSelector implements PluginTransactionSelector {
  private static final Logger LOG = LoggerFactory.getLogger(CredibleTransactionSelector.class);

  public static class Config {
    private final ISidecarStrategy strategy;
    private final CredibleMetrics metrics;

    public Config(ISidecarStrategy strategy, CredibleMetrics metrics) {
      this.strategy = strategy;
      this.metrics = metrics;
    }

    public ISidecarStrategy getStrategy() {
      return strategy;
    }

    public CredibleMetrics getMetrics() {
      return metrics;
    }
  }

  private final Config config;
  private final CredibleMetrics metrics;

  public CredibleTransactionSelector(final Config config) {
    this.config = config;
    this.metrics = config.getMetrics();
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext txContext) {
    metrics.recordTxPreProcessing();

    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        LOG.debug("Sending transaction {} for processing ", txHash);

        // Create request with proper models
        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(List.of(new TransactionWithHash(txEnv, txHash)));

        config.getStrategy().dispatchTransactions(sendRequest);
        
        LOG.debug("Started async transaction processing for {}", txHash);
    } catch (Exception e) {
        LOG.error("Error in transaction preprocessing for {}: {}", txHash, e.getMessage());
    }
    
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext txContext,
      final TransactionProcessingResult transactionProcessingResult) {
    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();
    
    try {
        LOG.debug("Awaiting result for {}", txHash);
        
        GetTransactionsResponse txResponse = config.getStrategy().getTransactionResults(Arrays.asList(txHash));

        if (txResponse == null ||
            txResponse.getResults() == null ||
            txResponse.getResults().isEmpty()
        ) {
            LOG.warn("Transaction {} not found in sidecar response", txHash);
            return TransactionSelectionResult.SELECTED;
        }

        return txResponse.getResults().stream()
          .filter(txResult -> txResult.getHash().equals(txHash))
          .findFirst()
          .map(res -> {
            var status = res.getStatus();
            if (TransactionStatus.ASSERTION_FAILED.equals(status) || 
                  TransactionStatus.FAILED.equals(status)) {
                  LOG.info("Transaction {} excluded due to status: {}", txHash, status);
                  metrics.recordTxPostExclusion(status);
                  // TODO: maybe return a more appropriate status
                  return TransactionSelectionResult.invalid("TX rejected by sidecar");
              } else {
                  LOG.debug("Transaction {} included with status: {}", txHash, status);
                  return TransactionSelectionResult.SELECTED;
              }
          })
          .orElse(TransactionSelectionResult.SELECTED);
    } catch (Exception e) {
        LOG.error("Error in transaction postprocessing for {}: {}", txHash, e.getMessage());
        return TransactionSelectionResult.SELECTED;
    }
  }

  @Override
  public void onTransactionNotSelected(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResult transactionSelectionResult) {

    metrics.recordReorgRequest();

    var transaction = evaluationContext.getPendingTransaction().getTransaction();
    String txHash = transaction.getHash().toHexString();
    String reason = transactionSelectionResult.toString();

    try {
        LOG.debug("Sending reorg request for transaction {} due to: {}", txHash, reason);

        // Create reorg request with the transaction hash
        ReorgRequest reorgRequest = new ReorgRequest(txHash);
        var reorgResponses = config.getStrategy().sendReorgRequest(reorgRequest);

        LOG.debug("Reorg request successful for transaction {}, got {} responses", txHash, reorgResponses.size());
    } catch (Exception e) {
        LOG.error("Failed to send reorg request for transaction {}: {}", txHash, e.getMessage(), e);
    }
  }
}
