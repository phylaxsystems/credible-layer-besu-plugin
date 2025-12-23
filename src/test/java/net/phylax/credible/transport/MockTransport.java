package net.phylax.credible.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import net.phylax.credible.types.SidecarApiModels.*;

public class MockTransport implements ISidecarTransport {
    private int processingLatency;
    // Separate latency for getTransaction fallback (if not set, uses processingLatency)
    private Integer getTransactionLatency = null;
    private String sendTxStatus = "accepted";
    private String getTxStatus = TransactionStatus.SUCCESS;
    private boolean reorgSuccess = true;
    private int sendTransactionsLatency = 0;
    private int sendEventsLatency = 0;

    // Whether to return empty results on getTransactions
    private boolean emptyResults = false;

    private boolean throwOnSendEvents = false;
    private boolean throwOnSendTx = false;
    private boolean throwOnGetTx = false;

    // List of tx hashes that return assertion_failed (stored as byte[])
    private List<byte[]> failingTransactions = new ArrayList<>();

    // Helper to check if a byte[] matches any in the failing list
    private boolean isFailingTransaction(byte[] txHash) {
        for (byte[] failing : failingTransactions) {
            if (Arrays.equals(txHash, failing)) {
                return true;
            }
        }
        return false;
    }

    // Results subscription callback
    private Consumer<TransactionResult> resultsCallback;
    private Consumer<Throwable> errorCallback;

    public MockTransport(int processingLatency) {
        this.processingLatency = processingLatency;
    }

    @Override
    public CompletableFuture<SendTransactionsResponse> sendTransactions(SendTransactionsRequest transactions) {
        Executor delayedExecutor = CompletableFuture.delayedExecutor(
            sendTransactionsLatency, TimeUnit.MILLISECONDS);

        return CompletableFuture.supplyAsync(() -> {
            if (throwOnSendTx) {
                throw new RuntimeException("SendTransactions failed");
            }

            // Simulate results coming via the stream after processing latency
            // Store callback reference and latency for use in async task (capture at send time)
            final Consumer<TransactionResult> callbackRef = resultsCallback;
            final int latency = processingLatency;
            if (callbackRef != null) {
                Executor resultExecutor = CompletableFuture.delayedExecutor(
                    latency, TimeUnit.MILLISECONDS);
                CompletableFuture.runAsync(() -> {
                    for (TransactionExecutionPayload tx : transactions.getTransactions()) {
                        TxExecutionId txExecId = tx.getTxExecutionId();
                        String status = getTxStatus;
                        if (isFailingTransaction(txExecId.getTxHash())) {
                            status = TransactionStatus.ASSERTION_FAILED;
                        }
                        TransactionResult result = new TransactionResult(txExecId, status, 21000L, "");
                        callbackRef.accept(result);
                    }
                }, resultExecutor);
            }

            return new SendTransactionsResponse(
                sendTxStatus,
                "Successfuly accepted",
                (long) transactions.getTransactions().size());
        }, delayedExecutor);

    }

    @Override
    public CompletableFuture<GetTransactionsResponse> getTransactions(GetTransactionsRequest req) {
        Executor delayedExecutor = CompletableFuture.delayedExecutor(
            processingLatency, TimeUnit.MILLISECONDS);

        List<TransactionResult> validResults = new ArrayList<>();

        if (!emptyResults) {
            for(TxExecutionId txExecutionId : req.getTxExecutionIds()) {
                validResults.add(new TransactionResult(txExecutionId, getTxStatus, 21000L, ""));
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            if (throwOnGetTx) {
                throw new RuntimeException("GetTransactions failed");
            }
            return new GetTransactionsResponse(validResults, new ArrayList<>());
        }, delayedExecutor);
    }

    @Override
    public CompletableFuture<GetTransactionResponse> getTransaction(GetTransactionRequest req) {
        int latency = getTransactionLatency != null ? getTransactionLatency : processingLatency;
        Executor delayedExecutor = CompletableFuture.delayedExecutor(
            latency, TimeUnit.MILLISECONDS);

        final TransactionResult result = new TransactionResult(req.toTxExecutionId(), getTxStatus, 21000L, "");

        if (isFailingTransaction(result.getTxExecutionId().getTxHash())) {
            result.setStatus(TransactionStatus.ASSERTION_FAILED);
        }

        return CompletableFuture.supplyAsync(() -> {
            if (throwOnGetTx) {
                throw new RuntimeException("GetTransaction failed");
            }
            return new GetTransactionResponse(emptyResults ? null : result);
        }, delayedExecutor);
    }

    @Override
    public CompletableFuture<ReorgResponse> sendReorg(ReorgRequest reorgRequest) {
        return CompletableFuture.completedFuture(new ReorgResponse(reorgSuccess, ""));
    }


    @Override
    public CompletableFuture<SendEventsResponse> sendEvents(SendEventsRequest events) {
        Executor delayedExecutor = CompletableFuture.delayedExecutor(
            processingLatency, TimeUnit.MILLISECONDS);

        return CompletableFuture.supplyAsync(() -> {
            if (throwOnSendEvents) {
                throw new RuntimeException("SendEvents failed");
            }
            return new SendEventsResponse(
            "accepted",
            "Request successfully processed",
            (long)events.getEvents().size());
        }, delayedExecutor);
    }

    @Override
    public CompletableFuture<Boolean> sendCommitHead(CommitHead commitHead) {
        Executor delayedExecutor = CompletableFuture.delayedExecutor(
            processingLatency, TimeUnit.MILLISECONDS);

        return CompletableFuture.supplyAsync(() -> {
            if (throwOnSendEvents) {
                throw new RuntimeException("SendCommitHead failed");
            }
            return true;
        }, delayedExecutor);
    }

    public void setSendTxStatus(String sendTxStatus) {
        this.sendTxStatus = sendTxStatus;
    }

    public void setGetTxStatus(String getTxStatus) {
        this.getTxStatus = getTxStatus;
    }

    public void setReorgSuccess(boolean reorgSuccess) {
        this.reorgSuccess = reorgSuccess;
    }

    public void setEmptyResults(boolean emptyResults) {
        this.emptyResults = emptyResults;
    }

    public void setThrowOnSendEvents(boolean throwOnSendEvents) {
        this.throwOnSendEvents = throwOnSendEvents;
    }

    public void setThrowOnSendTx(boolean throwOnSendTx) {
        this.throwOnSendTx = throwOnSendTx;
    }

    public void setThrowOnGetTx(boolean throwOnGetTx) {
        this.throwOnGetTx = throwOnGetTx;
    }

    public void setProcessingLatency(int processingLatency) {
        this.processingLatency = processingLatency;
    }

    public void setSendTransactionsLatency(int sendTransactionsLatency) {
        this.sendTransactionsLatency = sendTransactionsLatency;
    }

    public void setSendEventsLatency(int sendEventsLatency) {
        this.sendEventsLatency = sendEventsLatency;
    }

    public void setGetTransactionLatency(Integer getTransactionLatency) {
        this.getTransactionLatency = getTransactionLatency;
    }

    public void addFailingTx(byte[] failingTx) {
        failingTransactions.add(failingTx);
    }

    @Override
    public CompletableFuture<Void> subscribeResults(Consumer<TransactionResult> onResult, Consumer<Throwable> onError) {
        this.resultsCallback = onResult;
        this.errorCallback = onError;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void closeResultsSubscription() {
        this.resultsCallback = null;
        this.errorCallback = null;
    }

    /**
     * Simulate receiving a transaction result from the sidecar
     */
    public void simulateResult(TransactionResult result) {
        if (resultsCallback != null) {
            resultsCallback.accept(result);
        }
    }

    /**
     * Simulate a stream error
     */
    public void simulateError(Throwable error) {
        if (errorCallback != null) {
            errorCallback.accept(error);
        }
    }
}
