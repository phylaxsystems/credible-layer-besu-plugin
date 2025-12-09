package net.phylax.credible.transport.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.protobuf.ByteString;

import net.phylax.credible.types.SidecarApiModels;
import sidecar.transport.v1.Sidecar;

/**
 * Converter between Java POJOs (SidecarApiModels) and Protobuf messages
 */
public class GrpcModelConverter {


    // ==================== REQUEST CONVERSIONS (POJO → Protobuf) ====================

    /**
     * Convert BlockEnv POJO to BlockEnv protobuf.
     * Now works directly with byte[] fields - no hex string conversion needed.
     */
    private static Sidecar.BlockEnv toProtoBlockEnv(SidecarApiModels.BlockEnv blockEnv) {
        if (blockEnv == null) {
            return Sidecar.BlockEnv.getDefaultInstance();
        }

        Sidecar.BlockEnv.Builder builder = Sidecar.BlockEnv.newBuilder()
            .setNumber(longToByteString(blockEnv.getNumber(), 32))
            .setBeneficiary(bytesToByteString(blockEnv.getBeneficiary()))
            .setTimestamp(longToByteString(blockEnv.getTimestamp(), 32))
            .setGasLimit(blockEnv.getGasLimit())
            .setBasefee(blockEnv.getBaseFee())
            .setDifficulty(bytesToByteStringPadded(blockEnv.getDifficulty(), 32));

        if (blockEnv.getPrevrandao() != null) {
            builder.setPrevrandao(bytesToByteStringPadded(blockEnv.getPrevrandao(), 32));
        }

        if (blockEnv.getBlobExcessGasAndPrice() != null) {
            builder.setBlobExcessGasAndPrice(
                toProtoBlobExcessGasAndPrice(blockEnv.getBlobExcessGasAndPrice()));
        }

        return builder.build();
    }

    /**
     * Convert BlobExcessGasAndPrice POJO to protobuf
     */
    private static Sidecar.BlobExcessGasAndPrice toProtoBlobExcessGasAndPrice(
            SidecarApiModels.BlobExcessGasAndPrice pojo) {
        return Sidecar.BlobExcessGasAndPrice.newBuilder()
            .setExcessBlobGas(pojo.getExcessBlobGas())
            .setBlobGasprice(longToByteString(pojo.getBlobGasPrice(), 16))
            .build();
    }

    /**
     * Convert TransactionExecutionPayload POJO to Transaction protobuf
     */
    private static Sidecar.Transaction toProtoTransaction(SidecarApiModels.TransactionExecutionPayload pojo) {
        var builder = Sidecar.Transaction.newBuilder()
            .setTxExecutionId(toProtoTxExecutionId(pojo.getTxExecutionId()))
            .setTxEnv(toProtoTransactionEnv(pojo.getTxEnv()));

        if (pojo.getPrevTxHash() != null && pojo.getPrevTxHash().length > 0) {
            builder.setPrevTxHash(bytesToByteString(pojo.getPrevTxHash()));
        }

        return builder.build();
    }

    /**
     * Convert TxExecutionId POJO to protobuf
     */
    public static Sidecar.TxExecutionId toProtoTxExecutionId(SidecarApiModels.TxExecutionId pojo) {
        if (pojo == null) {
            return Sidecar.TxExecutionId.getDefaultInstance();
        }

        return Sidecar.TxExecutionId.newBuilder()
            .setBlockNumber(longToByteString(pojo.getBlockNumber(), 32))
            .setIterationId(pojo.getIterationId())
            .setTxHash(bytesToByteString(pojo.getTxHash()))
            .setIndex(pojo.getIndex())
            .build();
    }

    /**
     * Convert TxEnv POJO to TransactionEnv protobuf.
     * Now works directly with byte[] fields - no hex string conversion needed.
     */
    private static Sidecar.TransactionEnv toProtoTransactionEnv(SidecarApiModels.TxEnv pojo) {
        if (pojo == null) {
            return Sidecar.TransactionEnv.getDefaultInstance();
        }

        Sidecar.TransactionEnv.Builder builder = Sidecar.TransactionEnv.newBuilder()
            .setTxType(Byte.toUnsignedInt(pojo.getTxType()))
            .setCaller(bytesToByteString(pojo.getCaller()))
            .setGasLimit(pojo.getGasLimit())
            .setGasPrice(longToByteString(pojo.getGasPrice(), 16))
            .setTransactTo(bytesToByteString(pojo.getKind()))
            .setValue(bytesToByteStringPadded(pojo.getValue(), 32))
            .setData(bytesToByteString(pojo.getData()))
            .setNonce(pojo.getNonce())
            .setMaxFeePerBlobGas(longToByteString(pojo.getMaxFeePerBlobGas(), 16));

        if (pojo.getChainId() != null) {
            builder.setChainId(pojo.getChainId());
        }

        if (pojo.getGasPriorityFee() != null) {
            builder.setGasPriorityFee(longToByteString(pojo.getGasPriorityFee(), 16));
        }

        if (pojo.getBlobHashes() != null && !pojo.getBlobHashes().isEmpty()) {
            List<ByteString> blobHashes = pojo.getBlobHashes().stream()
                .map(h -> bytesToByteStringPadded(h, 32))
                .collect(Collectors.toList());
            builder.addAllBlobHashes(blobHashes);
        }

        if (pojo.getAccessList() != null && !pojo.getAccessList().isEmpty()) {
            List<Sidecar.AccessListItem> accessList = pojo.getAccessList().stream()
                .map(GrpcModelConverter::toProtoAccessListItem)
                .collect(Collectors.toList());
            builder.addAllAccessList(accessList);
        }

        if (pojo.getAuthorizationList() != null && !pojo.getAuthorizationList().isEmpty()) {
            List<Sidecar.Authorization> authorizationList = pojo.getAuthorizationList().stream()
                .map(GrpcModelConverter::toProtoAuthorization)
                .collect(Collectors.toList());
            builder.addAllAuthorizationList(authorizationList);
        }

        return builder.build();
    }

    /**
     * Convert AccessListEntry POJO to AccessListItem protobuf
     */
    private static Sidecar.AccessListItem toProtoAccessListItem(SidecarApiModels.AccessListEntry pojo) {
        List<ByteString> storageKeys = pojo.getStorageKeys() != null
            ? pojo.getStorageKeys().stream()
                .map(k -> bytesToByteStringPadded(k, 32))
                .collect(Collectors.toList())
            : new ArrayList<>();

        return Sidecar.AccessListItem.newBuilder()
            .setAddress(bytesToByteString(pojo.getAddress()))
            .addAllStorageKeys(storageKeys)
            .build();
    }

    private static Sidecar.Authorization toProtoAuthorization(SidecarApiModels.AuthorizationListEntry pojo) {
        Sidecar.Authorization.Builder builder = Sidecar.Authorization.newBuilder()
            .setAddress(bytesToByteString(pojo.getAddress()))
            .setYParity(Byte.toUnsignedInt(pojo.getV()))
            .setR(bytesToByteStringPadded(pojo.getR(), 32))
            .setS(bytesToByteStringPadded(pojo.getS(), 32));

        if (pojo.getChainId() != null) {
            builder.setChainId(longToByteString(pojo.getChainId(), 32));
        }

        if (pojo.getNonce() != null) {
            builder.setNonce(pojo.getNonce());
        }

        return builder.build();
    }

    /**
     * Convert List of TxExecutionId to GetTransactionsRequest protobuf
     */
    public static Sidecar.GetTransactionsRequest toProtoGetTransactionsRequest(SidecarApiModels.GetTransactionsRequest request) {
        List<Sidecar.TxExecutionId> protoIds = request != null
            ? request.getTxExecutionIds().stream()
                .map(GrpcModelConverter::toProtoTxExecutionId)
                .collect(Collectors.toList())
            : new ArrayList<>();

        return Sidecar.GetTransactionsRequest.newBuilder()
            .addAllTxExecutionIds(protoIds)
            .build();
    }

    /**
     * Convert TxExecutionId to GetTransactionRequest protobuf
     */
    public static Sidecar.GetTransactionRequest toProtoGetTransactionRequest(SidecarApiModels.GetTransactionRequest request) {
        var txExecId = Sidecar.TxExecutionId.newBuilder()
            .setBlockNumber(longToByteString(request.getBlockNumber(), 32))
            .setIterationId(request.getIterationId())
            .setTxHash(bytesToByteString(request.getTxHash()))
            .setIndex(request.getIndex());
        return Sidecar.GetTransactionRequest.newBuilder()
            .setTxExecutionId(txExecId)
            .build();
    }

    /**
     * Convert SendEventsRequestItem POJO to Event protobuf for streaming API
     */
    public static Sidecar.Event toProtoEvent(SidecarApiModels.SendEventsRequestItem item) {
        Sidecar.Event.Builder builder = Sidecar.Event.newBuilder();

        if (item instanceof SidecarApiModels.CommitHeadReqItem) {
            SidecarApiModels.CommitHeadReqItem commitHeadItem = (SidecarApiModels.CommitHeadReqItem) item;
            if (commitHeadItem.getCommitHead() != null) {
                builder.setCommitHead(toProtoCommitHead(commitHeadItem.getCommitHead()));
            }
        } else if (item instanceof SidecarApiModels.NewIterationReqItem) {
            SidecarApiModels.NewIterationReqItem newIterationItem = (SidecarApiModels.NewIterationReqItem) item;
            if (newIterationItem.getNewIteration() != null) {
                builder.setNewIteration(toProtoNewIteration(newIterationItem.getNewIteration()));
            }
        } else if (item instanceof SidecarApiModels.TransactionReqItem) {
            SidecarApiModels.TransactionReqItem transactionItem = (SidecarApiModels.TransactionReqItem) item;
            if (transactionItem.getTransaction() != null) {
                builder.setTransaction(toProtoTransaction(transactionItem.getTransaction()));
            }
        } else if (item instanceof SidecarApiModels.ReorgEventReqItem) {
            SidecarApiModels.ReorgEventReqItem reorgItem = (SidecarApiModels.ReorgEventReqItem) item;
            if (reorgItem.getReorg() != null) {
                builder.setReorg(toProtoReorgEvent(reorgItem.getReorg()));
            }
        }

        return builder.build();
    }

    /**
     * Convert ReorgEvent POJO to ReorgEvent protobuf
     */
    private static Sidecar.ReorgEvent toProtoReorgEvent(SidecarApiModels.ReorgEvent reorg) {
        return Sidecar.ReorgEvent.newBuilder()
            .setTxExecutionId(toProtoTxExecutionId(reorg.getTxExecutionId()))
            .build();
    }

    /**
     * Convert CommitHead POJO to protobuf
     */
    private static Sidecar.CommitHead toProtoCommitHead(SidecarApiModels.CommitHead pojo) {
        Sidecar.CommitHead.Builder builder = Sidecar.CommitHead.newBuilder()
            .setBlockNumber(longToByteString(pojo.getBlockNumber(), 32))
            .setNTransactions(pojo.getNTransactions());

        if (pojo.getLastTxHash() != null && pojo.getLastTxHash().length > 0) {
            builder.setLastTxHash(bytesToByteString(pojo.getLastTxHash()));
        }

        if (pojo.getSelectedIterationId() != null) {
            builder.setSelectedIterationId(pojo.getSelectedIterationId());
        }

        if (pojo.getBlockHash() != null && pojo.getBlockHash().length > 0) {
            builder.setBlockHash(bytesToByteStringPadded(pojo.getBlockHash(), 32));
        }

        if (pojo.getParentBeaconBlockRoot() != null && pojo.getParentBeaconBlockRoot().length > 0) {
            builder.setParentBeaconBlockRoot(bytesToByteStringPadded(pojo.getParentBeaconBlockRoot(), 32));
        }

        if (pojo.getTimestamp() != null) {
            builder.setTimestamp(longToByteString(pojo.getTimestamp(), 32));
        }

        return builder.build();
    }

    /**
     * Convert NewIteration POJO to protobuf
     */
    private static Sidecar.NewIteration toProtoNewIteration(SidecarApiModels.NewIteration pojo) {
        return Sidecar.NewIteration.newBuilder()
            .setIterationId(pojo.getIterationId())
            .setBlockEnv(toProtoBlockEnv(pojo.getBlockEnv()))
            .build();
    }

    // ==================== RESPONSE CONVERSIONS (Protobuf → POJO) ====================

    /**
     * Convert StreamAck protobuf to SendEventsResponse POJO
     */
    public static SidecarApiModels.SendEventsResponse fromProtoStreamAckToSendEventsResponse(Sidecar.StreamAck proto) {
        return new SidecarApiModels.SendEventsResponse(
            proto.getSuccess() ? "accepted" : "failed",
            proto.getMessage().isEmpty() ? null : proto.getMessage(),
            proto.getEventsProcessed()
        );
    }

    /**
     * Convert GetTransactionsResponse protobuf to POJO
     */
    public static SidecarApiModels.GetTransactionsResponse fromProtoGetTransactionsResponse(
            Sidecar.GetTransactionsResponse proto) {
        List<SidecarApiModels.TransactionResult> results = proto.getResultsList().stream()
            .map(GrpcModelConverter::fromProtoTransactionResult)
            .collect(Collectors.toList());

        // Convert ByteString list to byte[] list directly
        List<byte[]> notFound = proto.getNotFoundList().stream()
            .map(ByteString::toByteArray)
            .collect(Collectors.toList());

        return new SidecarApiModels.GetTransactionsResponse(
            results,
            notFound
        );
    }

    /**
     * Convert GetTransactionResponse protobuf to POJO
     */
    public static SidecarApiModels.GetTransactionResponse fromProtoGetTransactionResponse(
            Sidecar.GetTransactionResponse proto) {
        SidecarApiModels.TransactionResult result = fromProtoTransactionResult(proto.getResult());
        return new SidecarApiModels.GetTransactionResponse(result);
    }

    /**
     * Convert TransactionResult protobuf to POJO
     */
    public static SidecarApiModels.TransactionResult fromProtoTransactionResult(
            Sidecar.TransactionResult proto) {
        SidecarApiModels.TxExecutionId txExecutionId = fromProtoTxExecutionId(proto.getTxExecutionId());
        // Convert ResultStatus enum to string
        String status = resultStatusToString(proto.getStatus());
        return new SidecarApiModels.TransactionResult(
            txExecutionId,
            status,
            proto.getGasUsed(),
            proto.getError().isEmpty() ? null : proto.getError()
        );
    }

    /**
     * Convert ResultStatus enum to string representation
     */
    private static String resultStatusToString(Sidecar.ResultStatus status) {
        switch (status) {
            case RESULT_STATUS_SUCCESS:
                return SidecarApiModels.TransactionStatus.SUCCESS;
            case RESULT_STATUS_REVERTED:
                return SidecarApiModels.TransactionStatus.REVERTED;
            case RESULT_STATUS_HALTED:
                return SidecarApiModels.TransactionStatus.HALTED;
            case RESULT_STATUS_FAILED:
                return SidecarApiModels.TransactionStatus.FAILED;
            case RESULT_STATUS_ASSERTION_FAILED:
                return SidecarApiModels.TransactionStatus.ASSERTION_FAILED;
            default:
                return "unknown";
        }
    }

    /**
     * Convert TxExecutionId protobuf to POJO
     */
    private static SidecarApiModels.TxExecutionId fromProtoTxExecutionId(
            Sidecar.TxExecutionId proto) {
        // Convert ByteString txHash directly to byte[]
        return new SidecarApiModels.TxExecutionId(
            byteStringToLong(proto.getBlockNumber()),
            proto.getIterationId(),
            proto.getTxHash().toByteArray(),
            proto.getIndex()
        );
    }

    // ==================== HELPER METHODS ====================

    /**
     * Convert byte array to ByteString directly (no conversion needed).
     * This is the efficient path - just wraps the bytes.
     */
    private static ByteString bytesToByteString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return ByteString.EMPTY;
        }
        return ByteString.copyFrom(bytes);
    }

    /**
     * Convert byte array to ByteString with left-padding to specified length.
     * Used for fixed-size fields like addresses (20 bytes), hashes (32 bytes), etc.
     * @param bytes the source byte array
     * @param targetLength target byte length for left-padding with zeros
     */
    private static ByteString bytesToByteStringPadded(byte[] bytes, int targetLength) {
        if (bytes == null || bytes.length == 0) {
            return ByteString.copyFrom(new byte[targetLength]);
        }
        if (bytes.length >= targetLength) {
            // Already at or exceeds target length, use as-is
            return ByteString.copyFrom(bytes);
        }
        // Need to left-pad with zeros
        byte[] padded = new byte[targetLength];
        int offset = targetLength - bytes.length;
        System.arraycopy(bytes, 0, padded, offset, bytes.length);
        return ByteString.copyFrom(padded);
    }

    /**
     * Convert long to ByteString (big-endian) with specified padding.
     * @param value the long value to convert
     * @param padding target byte length (e.g., 16 for u128, 32 for U256)
     */
    private static ByteString longToByteString(Long value, int padding) {
        if (value == null) {
            return ByteString.copyFrom(new byte[padding]);
        }
        byte[] bytes = new byte[padding];
        long v = value;
        for (int i = padding - 1; i >= padding - 8 && i >= 0; i--) {
            bytes[i] = (byte) (v & 0xFF);
            v >>= 8;
        }
        // Upper bytes are zero for values that fit in long
        return ByteString.copyFrom(bytes);
    }

    /**
     * Convert ByteString (big-endian) to long.
     * @param byteString the ByteString to convert
     * @return the long value
     */
    private static Long byteStringToLong(ByteString byteString) {
        if (byteString == null || byteString.isEmpty()) {
            return 0L;
        }
        byte[] bytes = byteString.toByteArray();
        long result = 0;
        int start = Math.max(0, bytes.length - 8);
        for (int i = start; i < bytes.length; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }
}
