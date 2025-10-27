package net.phylax.credible.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import net.phylax.credible.types.SidecarApiModels.*;

public class MockTransport implements ISidecarTransport {
    private int processingLatency;
    private boolean blockEnvSuccess = true;
    private String sendTxStatus = "accepted";
    private String getTxStatus = TransactionStatus.SUCCESS;
    private boolean reorgSuccess = true;
    private int sendTransactionsLatency = 0;
    private int sendBlockEnvLatency = 0;

    // Whether to return empty results on getTransactions
    private boolean emptyResults = false;

    private boolean throwOnSendBlockEnv = false;
    private boolean throwOnSendTx = false;
    private boolean throwOnGetTx = false;
    
    public MockTransport(int processingLatency) {
        this.processingLatency = processingLatency;
    }

    @Override
    public CompletableFuture<SendBlockEnvResponse> sendBlockEnv(SendBlockEnvRequest blockEnv) {
        Executor delayedExecutor = CompletableFuture.delayedExecutor(
            sendBlockEnvLatency, TimeUnit.MILLISECONDS);

        return CompletableFuture.supplyAsync(() -> {
            if (throwOnSendBlockEnv) {
                throw new RuntimeException("SendBlockEnv failed");
            }
            return new SendBlockEnvResponse("accepted", 1L, "BlockEnv successfully accepted");
        }, delayedExecutor);
    }

    @Override
    public CompletableFuture<SendTransactionsResponse> sendTransactions(SendTransactionsRequest transactions) {
        Executor delayedExecutor = CompletableFuture.delayedExecutor(
            sendTransactionsLatency, TimeUnit.MILLISECONDS);
            
        return CompletableFuture.supplyAsync(() -> {
            if (throwOnSendTx) {
                throw new RuntimeException("SendTransactions failed");
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
        Executor delayedExecutor = CompletableFuture.delayedExecutor(
            processingLatency, TimeUnit.MILLISECONDS);

        final TransactionResult result;

        if (!emptyResults) {
            result = new TransactionResult(req.toTxExecutionId(), getTxStatus, 21000L, "");
        } else {
            result = null;
        }

        return CompletableFuture.supplyAsync(() -> {
            if (throwOnGetTx) {
                throw new RuntimeException("GetTransactions failed");
            }
            return new GetTransactionResponse(result);
        }, delayedExecutor);
    }

    @Override
    public CompletableFuture<ReorgResponse> sendReorg(ReorgRequest reorgRequest) {
        return CompletableFuture.completedFuture(new ReorgResponse(reorgSuccess, ""));
    }

    public void setBlockEnvSuccess(boolean blockEnvSuccess) {
        this.blockEnvSuccess = blockEnvSuccess;
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

    public void setThrowOnSendBlockEnv(boolean throwOnSendBlockEnv) {
        this.throwOnSendBlockEnv = throwOnSendBlockEnv;
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

    public void setSendBlockEnvLatency(int sendBlockEnvLatency) {
        this.sendBlockEnvLatency = sendBlockEnvLatency;
    }
}
