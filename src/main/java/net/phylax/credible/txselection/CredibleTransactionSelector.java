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

public class CredibleTransactionSelector implements PluginTransactionSelector {
  private static final Logger LOG = LoggerFactory.getLogger(CredibleTransactionSelector.class);

  public static class Config {
    private final int processingTimeout;
    private JsonRpcTransport sidecarClient;

    public Config(JsonRpcTransport sidecarClient, int processingTimeout) {
      this.processingTimeout = processingTimeout;
      this.sidecarClient = sidecarClient;
    }

    public int getProcessingTimeout() {
      return processingTimeout;
    }

    public JsonRpcTransport getSidecarClient() {
      return sidecarClient;
    }
  }

  private final Config config;
  
  // Store pending getTransactions futures by transaction hash
  private final Map<String, CompletableFuture<GetTransactionsResponse>> pendingTxRequests = 
        new ConcurrentHashMap<>();

  public CredibleTransactionSelector(final Config config) {
    this.config = config;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext txContext) {

    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        LOG.debug("Sending transaction {} for processing ", txHash);

        // Create request with proper models
        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(List.of(new TransactionWithHash(txEnv, txHash)));
        
        // 1. Send transaction synchronously via sendTransactions
        SendTransactionsResponse sendResponse = config.sidecarClient.call(
          CredibleLayerMethods.SEND_TRANSACTIONS, 
          sendRequest, 
          SendTransactionsResponse.class
        );

        // Check if transaction was queued successfully
        if (!"accepted".equals(sendResponse.getStatus())) {
            LOG.warn("Transaction {} failed to queue for processing", txHash);
            return TransactionSelectionResult.SELECTED;
        }

        // 2. Get transaction status asynchronously via getTransactions
        List<String> params = Arrays.asList(txHash);

        CompletableFuture<GetTransactionsResponse> future = config.sidecarClient.callAsync(
          CredibleLayerMethods.GET_TRANSACTIONS,
          params,
          GetTransactionsResponse.class
        );
        
        // Store future for postprocessing
        pendingTxRequests.put(txHash, future);
        
        LOG.debug("Started async transaction processing for {}", txHash);
    } catch (JsonRpcTransport.JsonRpcException e) {
        LOG.warn("JsonRpcException for {}: {}: {}", txHash, e.getMessage(), e.getError());
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
      CompletableFuture<GetTransactionsResponse> future = pendingTxRequests.remove(txHash);
      
      if (future == null) {
          LOG.warn("No pending request found for transaction {}, allowing", txHash);
          return TransactionSelectionResult.SELECTED;
      }
      
      try {
          LOG.debug("Awaiting result for {}", txHash);
          
          // Wait for long-polling response
          GetTransactionsResponse response = future.get(config.processingTimeout, TimeUnit.MILLISECONDS);
          
          // Process the response to determine if transaction is valid
          if (response.getResults() == null || response.getResults().isEmpty()) {
              LOG.warn("No results for transaction {} but allowing", txHash);
              return TransactionSelectionResult.SELECTED;
          }
          
          // Find the result for our specific transaction hash
          for (TransactionResult txResult : response.getResults()) {
              if (txHash.equals(txResult.getHash())) {
                  String status = txResult.getStatus();
                  
                  if (TransactionStatus.ASSERTION_FAILED.equals(status) || 
                      TransactionStatus.FAILED.equals(status)) {
                      LOG.info("Transaction {} excluded due to status: {}", txHash, status);
                      // TODO: maybe return a more appropriate status
                      return TransactionSelectionResult.invalid("TX rejected by sidecar");
                  } else {
                      LOG.debug("Transaction {} included with status: {}", txHash, status);
                      return TransactionSelectionResult.SELECTED;
                  }
              }
          }
          
          LOG.warn("Transaction {} not found in results, but allowing", txHash);
          return TransactionSelectionResult.SELECTED;
    } catch (TimeoutException e) {
        LOG.warn("Fetching result from sidecar timed out {}", txHash);
        return TransactionSelectionResult.SELECTED;
    } catch (Exception e) {
        LOG.error("Error in transaction postprocessing for {}: {}", txHash, e.getMessage());
        return TransactionSelectionResult.SELECTED;
    }
  }
}