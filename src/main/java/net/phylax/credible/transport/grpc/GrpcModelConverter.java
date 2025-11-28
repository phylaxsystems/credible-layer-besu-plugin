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
    private static final ThreadLocal<Sidecar.TransactionEnv.Builder> BUILDER_POOL =
        ThreadLocal.withInitial(() -> Sidecar.TransactionEnv.newBuilder());


    // ==================== REQUEST CONVERSIONS (POJO → Protobuf) ====================

    /**
     * Convert BlockEnv POJO to BlockEnv protobuf
     */
    private static Sidecar.BlockEnv toProtoBlockEnv(SidecarApiModels.BlockEnv blockEnv) {
        if (blockEnv == null) {
            return Sidecar.BlockEnv.getDefaultInstance();
        }

        Sidecar.BlockEnv.Builder builder = Sidecar.BlockEnv.newBuilder()
            .setNumber(blockEnv.getNumber())
            .setBeneficiary(hexToByteString(blockEnv.getBeneficiary()))
            .setTimestamp(blockEnv.getTimestamp())
            .setGasLimit(blockEnv.getGasLimit())
            .setBasefee(blockEnv.getBaseFee())
            .setDifficulty(hexToByteString(blockEnv.getDifficulty(), 32));

        if (blockEnv.getPrevrandao() != null) {
            builder.setPrevrandao(hexToByteString(blockEnv.getPrevrandao(), 32));
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

        if (pojo.getPrevTxHash() != null) {
            builder.setPrevTxHash(hexToByteString(pojo.getPrevTxHash()));
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
            .setBlockNumber(pojo.getBlockNumber())
            .setIterationId(pojo.getIterationId())
            .setTxHash(hexToByteString(pojo.getTxHash()))
            .setIndex(pojo.getIndex())
            .build();
    }

    /**
     * Convert TxEnv POJO to TransactionEnv protobuf
     */
    private static Sidecar.TransactionEnv toProtoTransactionEnv(SidecarApiModels.TxEnv pojo) {
        if (pojo == null) {
            return Sidecar.TransactionEnv.getDefaultInstance();
        }

        Sidecar.TransactionEnv.Builder builder = BUILDER_POOL.get()
            .setTxType(Byte.toUnsignedInt(pojo.getTxType()))
            .setCaller(hexToByteString(pojo.getCaller()))
            .setGasLimit(pojo.getGasLimit())
            .setGasPrice(longToByteString(pojo.getGasPrice(), 16))
            .setTransactTo(hexToByteString(pojo.getKind()))
            .setValue(hexToByteString(pojo.getValue(), 32))
            .setData(hexToByteString(pojo.getData()))
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
                .map(h -> hexToByteString(h, 32))
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
                .map(k -> hexToByteString(k, 32))
                .collect(Collectors.toList())
            : new ArrayList<>();

        return Sidecar.AccessListItem.newBuilder()
            .setAddress(hexToByteString(pojo.getAddress()))
            .addAllStorageKeys(storageKeys)
            .build();
    }

    private static Sidecar.Authorization toProtoAuthorization(SidecarApiModels.AuthorizationListEntry pojo) {
        Sidecar.Authorization.Builder builder = Sidecar.Authorization.newBuilder()
            .setAddress(hexToByteString(pojo.getAddress()))
            .setYParity(Byte.toUnsignedInt(pojo.getV()))
            .setR(hexToByteString(pojo.getR(), 32))
            .setS(hexToByteString(pojo.getS(), 32));

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
            .setBlockNumber(request.getBlockNumber())
            .setIterationId(request.getIterationId())
            .setTxHash(hexToByteString(request.getTxHash()))
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
            .setBlockNumber(pojo.getBlockNumber())
            .setNTransactions(pojo.getNTransactions());

        if (pojo.getLastTxHash() != null && !pojo.getLastTxHash().isEmpty()) {
            builder.setLastTxHash(hexToByteString(pojo.getLastTxHash()));
        }

        if (pojo.getSelectedIterationId() != null) {
            builder.setSelectedIterationId(pojo.getSelectedIterationId());
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

        // Convert ByteString list to hex String list
        List<String> notFound = proto.getNotFoundList().stream()
            .map(bs -> "0x" + bytesToHex(bs.toByteArray()))
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
        // Convert ByteString txHash to hex string
        String txHash = "0x" + bytesToHex(proto.getTxHash().toByteArray());
        return new SidecarApiModels.TxExecutionId(
            proto.getBlockNumber(),
            proto.getIterationId(),
            txHash,
            proto.getIndex()
        );
    }

    // ==================== HELPER METHODS ====================

    /**
     * Convert hex string to ByteString (no padding)
     */
    private static ByteString hexToByteString(String hex) {
        return hexToByteString(hex, 0);
    }

    /**
     * Convert hex string to ByteString with optional left-padding
     * @param hex the hex string (with or without 0x prefix)
     * @param padding target byte length for left-padding with zeros (0 = no padding)
     */
    private static ByteString hexToByteString(String hex, int padding) {
        if (hex == null || hex.isEmpty()) {
            if (padding > 0) {
                return ByteString.copyFrom(new byte[padding]);
            }
            return ByteString.EMPTY;
        }
        // Remove 0x prefix if present
        String cleanHex = hex.startsWith("0x") || hex.startsWith("0X")
            ? hex.substring(2)
            : hex;

        if (cleanHex.isEmpty()) {
            if (padding > 0) {
                return ByteString.copyFrom(new byte[padding]);
            }
            return ByteString.EMPTY;
        }

        // Pad with leading zero if odd length
        if (cleanHex.length() % 2 != 0) {
            cleanHex = "0" + cleanHex;
        }

        int hexByteLen = cleanHex.length() / 2;
        int resultLen = padding > 0 ? Math.max(padding, hexByteLen) : hexByteLen;
        byte[] bytes = new byte[resultLen];

        // Calculate offset for left-padding (big-endian)
        int offset = resultLen - hexByteLen;

        for (int i = 0; i < hexByteLen; i++) {
            int index = i * 2;
            bytes[offset + i] = (byte) Integer.parseInt(cleanHex.substring(index, index + 2), 16);
        }
        return ByteString.copyFrom(bytes);
    }

    /**
     * Convert long to ByteString (big-endian) with specified padding
     * @param value the long value to convert
     * @param padding target byte length (e.g., 16 for u128, 32 for U256)
     */
    private static ByteString longToByteString(Long value, int padding) {
        if (value == null) {
            if (padding > 0) {
                return ByteString.copyFrom(new byte[padding]);
            }
            return ByteString.EMPTY;
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
     * Convert byte array to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
