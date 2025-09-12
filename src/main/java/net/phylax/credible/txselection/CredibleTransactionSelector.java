package net.phylax.credible.txselection;

import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import net.phylax.credible.transport.jsonrpc.JsonRpcTransport;
import net.phylax.credible.*;
import net.phylax.credible.types.TransactionConverter;
import net.phylax.credible.types.SidecarApiModels.*;
import net.phylax.credible.strategy.ISidecarStrategy;

public class CredibleTransactionSelector implements PluginTransactionSelector {
  private static final Logger LOG = LoggerFactory.getLogger(CredibleTransactionSelector.class);

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
  
  // Store pending getTransactions futures by transaction hash
  private final Map<String, List<CompletableFuture<GetTransactionsResponse>>> pendingTxRequests = 
        new ConcurrentHashMap<>();

  public CredibleTransactionSelector(final Config config) {
    this.config = config;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext txContext) {
    long startTime = System.currentTimeMillis();
    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        LOG.debug("Sending transaction {} for processing ", txHash);

        // Create request with proper models
        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(List.of(new TransactionWithHash(txEnv, txHash)));

        var futures = config.strategy.dispatchTransactions(sendRequest);
        
        // Store future for postprocessing
        pendingTxRequests.put(txHash, futures);
        
        LOG.debug("Started async transaction processing for {}", txHash);
    } catch (Exception e) {
        LOG.error("Error in transaction preprocessing for {}: {}", txHash, e.getMessage());
    }
    
    long latency = System.currentTimeMillis() - startTime;
    LOG.debug("evaluateTransactionPreProcessingMetrics {}ms", latency);
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext txContext,
      final TransactionProcessingResult transactionProcessingResult) {
    long startTime = System.currentTimeMillis();

    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();
    List<CompletableFuture<GetTransactionsResponse>> futures = pendingTxRequests.remove(txHash);
    
    if (futures == null || futures.isEmpty()) {
        LOG.warn("No pending request found for transaction {}, allowing", txHash);
        return TransactionSelectionResult.SELECTED;
    }
    
    try {
        LOG.debug("Awaiting result for {}", txHash);
        
        List<TransactionResult> results = config.strategy.handleTransportResponses(futures);
        for (TransactionResult txResult : results) {
            if (txHash.equals(txResult.getHash())) {
                String status = txResult.getStatus();
                
                if (TransactionStatus.ASSERTION_FAILED.equals(status) || 
                    TransactionStatus.FAILED.equals(status)) {
                    LOG.info("Transaction {} excluded due to status: {}", txHash, status);
                    // TODO: maybe return a more appropriate status
                    return TransactionSelectionResult.invalid("TX rejected by sidecar");
                } else {
                    LOG.debug("Transaction {} included with status: {}", txHash, status);
                    long latency = System.currentTimeMillis() - startTime;
                    LOG.debug("evaluateTransactionPostProcessingMetrics {}ms", latency);
                    return TransactionSelectionResult.SELECTED;
                }
            }
        }
        return TransactionSelectionResult.SELECTED;
    } catch (Exception e) {
        LOG.error("Error in transaction postprocessing for {}: {}", txHash, e.getMessage());
        return TransactionSelectionResult.SELECTED;
    }
  }
}