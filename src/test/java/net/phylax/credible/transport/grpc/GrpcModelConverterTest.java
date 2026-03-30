package net.phylax.credible.transport.grpc;

import java.lang.reflect.Method;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import net.phylax.credible.types.SidecarApiModels;
import sidecar.transport.v1.Sidecar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GrpcModelConverterTest {

    @Test
    public void fromProtoGetTransactionResponsePreservesResultOutcome() {
        SidecarApiModels.GetTransactionResponse response = GrpcModelConverter.fromProtoGetTransactionResponse(
            Sidecar.GetTransactionResponse.newBuilder()
                .setResult(testTransactionResult())
                .build()
        );

        assertNotNull(response.getResult());
        assertEquals(SidecarApiModels.TransactionStatus.SUCCESS, response.getResult().getStatus());
        assertEquals(21_000L, response.getResult().getGasUsed());
    }

    @Test
    public void fromProtoGetTransactionResponsePreservesNotFoundOutcome() throws Exception {
        byte[] missingHash = new byte[] {0x01, 0x23, 0x45, 0x67};

        SidecarApiModels.GetTransactionResponse response = GrpcModelConverter.fromProtoGetTransactionResponse(
            Sidecar.GetTransactionResponse.newBuilder()
                .setNotFound(ByteString.copyFrom(missingHash))
                .build()
        );

        assertNull(response.getResult());
        assertArrayEquals(missingHash, invokeGetNotFound(response));
    }

    @Test
    public void fromProtoGetTransactionResponseLeavesUnsetOutcomeEmpty() throws Exception {
        SidecarApiModels.GetTransactionResponse response = GrpcModelConverter.fromProtoGetTransactionResponse(
            Sidecar.GetTransactionResponse.getDefaultInstance()
        );

        assertNull(response.getResult());
        assertNull(invokeGetNotFound(response));
    }

    private static Sidecar.TransactionResult testTransactionResult() {
        return Sidecar.TransactionResult.newBuilder()
            .setTxExecutionId(Sidecar.TxExecutionId.newBuilder()
                .setBlockNumber(ByteString.copyFrom(new byte[] {0x01}))
                .setIterationId(7L)
                .setTxHash(ByteString.copyFrom(new byte[] {0x0A, 0x0B}))
                .setIndex(3L)
                .build())
            .setStatus(Sidecar.ResultStatus.RESULT_STATUS_SUCCESS)
            .setGasUsed(21_000L)
            .build();
    }

    private static byte[] invokeGetNotFound(SidecarApiModels.GetTransactionResponse response) throws Exception {
        Method method = response.getClass().getMethod("getNotFound");
        return (byte[]) method.invoke(response);
    }
}
