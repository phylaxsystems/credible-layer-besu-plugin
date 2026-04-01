package net.phylax.credible.aeges;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.transport.grpc.BaseGrpcTransport;

import aeges.v1.Aeges;
import aeges.v1.AegesServiceGrpc;


/**
 * gRPC client for the Aeges service using unary RPCs.
 */
@Slf4j
public class AegesGrpcClient extends BaseGrpcTransport {
    private final AegesServiceGrpc.AegesServiceBlockingStub stub;
    private final AtomicLong eventIdCounter = new AtomicLong(0);

    public AegesGrpcClient(ManagedChannel channel, long deadlineMillis) {
        super(channel, deadlineMillis);
        this.stub = AegesServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Send a transaction for verification.
     *
     * @return the response, or null if the service is unavailable or times out (fail-open)
     */
    public Aeges.VerifyTransactionResponse verifyTransaction(Aeges.Transaction protoTx) {
        Aeges.VerifyTransactionRequest request = Aeges.VerifyTransactionRequest.newBuilder()
            .setEventId(eventIdCounter.incrementAndGet())
            .setTransaction(protoTx)
            .build();

        try {
            return stub
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .verifyTransaction(request);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.DEADLINE_EXCEEDED) {
                log.warn("Aeges verification timed out after {}ms", deadlineMillis);
            } else {
                log.error("Aeges verification error: {}", getErrorMessage(e), e);
            }
            return null;
        } catch (Exception e) {
            log.error("Unexpected error during Aeges verification: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Shut down the channel.
     */
    public void close() {
        shutdownChannel();
    }
}
