package net.phylax.credible.transport.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.phylax.credible.types.SidecarApiModels;
import sidecar.transport.v1.Sidecar;

/**
 * Converter between Java POJOs (SidecarApiModels) and Protobuf messages
 */
public class GrpcModelConverter {

    // ==================== REQUEST CONVERSIONS (POJO → Protobuf) ====================

    /**
     * Convert SendBlockEnvRequest POJO to BlockEnvEnvelope protobuf
     */
    public static Sidecar.BlockEnvEnvelope toProtoBlockEnvEnvelope(SidecarApiModels.SendBlockEnvRequest request) {
        Sidecar.BlockEnvEnvelope.Builder builder = Sidecar.BlockEnvEnvelope.newBuilder()
            .setBlockEnv(toProtoBlockEnv(request))
            .setNTransactions(request.getNTransactions() != null ? request.getNTransactions() : 0);

        if (request.getLastTxHash() != null && !request.getLastTxHash().isEmpty()) {
            builder.setLastTxHash(request.getLastTxHash());
        }

        return builder.build();
    }

    /**
     * Convert SendBlockEnvRequest to BlockEnv protobuf
     */
    private static Sidecar.BlockEnv toProtoBlockEnv(SidecarApiModels.SendBlockEnvRequest request) {
        Sidecar.BlockEnv.Builder builder = Sidecar.BlockEnv.newBuilder()
            .setNumber(request.getNumber() != null ? request.getNumber() : 0L)
            .setBeneficiary(request.getBeneficiary() != null ? request.getBeneficiary() : "")
            .setTimestamp(request.getTimestamp() != null ? request.getTimestamp() : 0L)
            .setGasLimit(request.getGasLimit() != null ? request.getGasLimit() : 0L)
            .setBasefee(request.getBaseFee() != null ? request.getBaseFee() : 0L)
            .setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : "0");

        if (request.getPrevrandao() != null) {
            builder.setPrevrandao(request.getPrevrandao());
        }

        // Note: BlobExcessGasAndPrice not available in SendBlockEnvRequest currently
        // Will need to be added if required

        return builder.build();
    }

    /**
     * Convert BlobExcessGasAndPrice POJO to protobuf
     */
    private static Sidecar.BlobExcessGasAndPrice toProtoBlobExcessGasAndPrice(
            SidecarApiModels.BlobExcessGasAndPrice pojo) {
        return Sidecar.BlobExcessGasAndPrice.newBuilder()
            .setExcessBlobGas(pojo.getExcessBlobGas() != null ? pojo.getExcessBlobGas() : 0L)
            .setBlobGasprice(pojo.getBlobGasPrice() != null ? String.valueOf(pojo.getBlobGasPrice()) : "0")
            .build();
    }

    /**
     * Convert SendTransactionsRequest POJO to protobuf
     */
    public static Sidecar.SendTransactionsRequest toProtoSendTransactionsRequest(
            SidecarApiModels.SendTransactionsRequest request) {
        List<Sidecar.Transaction> transactions = request.getTransactions().stream()
            .map(GrpcModelConverter::toProtoTransaction)
            .collect(Collectors.toList());

        return Sidecar.SendTransactionsRequest.newBuilder()
            .addAllTransactions(transactions)
            .build();
    }

    /**
     * Convert TransactionWithHash POJO to Transaction protobuf
     */
    private static Sidecar.Transaction toProtoTransaction(SidecarApiModels.TransactionWithHash pojo) {
        return Sidecar.Transaction.newBuilder()
            .setHash(pojo.getHash() != null ? pojo.getHash() : "")
            .setTxEnv(toProtoTransactionEnv(pojo.getTxEnv()))
            .build();
    }

    /**
     * Convert TxEnv POJO to TransactionEnv protobuf
     */
    private static Sidecar.TransactionEnv toProtoTransactionEnv(SidecarApiModels.TxEnv pojo) {
        Sidecar.TransactionEnv.Builder builder = Sidecar.TransactionEnv.newBuilder()
            .setTxType(0) // Default to legacy, would need to be set if available in POJO
            .setCaller(pojo.getCaller() != null ? pojo.getCaller() : "")
            .setGasLimit(pojo.getGasLimit() != null ? pojo.getGasLimit() : 0L)
            .setGasPrice(pojo.getGasPrice() != null ? pojo.getGasPrice() : "0")
            .setKind(pojo.getTransactTo() != null ? pojo.getTransactTo() : "")
            .setValue(pojo.getValue() != null ? pojo.getValue() : "0")
            .setData(pojo.getData() != null ? pojo.getData() : "")
            .setNonce(pojo.getNonce() != null ? pojo.getNonce() : 0L)
            .setMaxFeePerBlobGas("0");

        if (pojo.getChainId() != null) {
            builder.setChainId(pojo.getChainId());
        }

        if (pojo.getAccessList() != null && !pojo.getAccessList().isEmpty()) {
            List<Sidecar.AccessListItem> accessList = pojo.getAccessList().stream()
                .map(GrpcModelConverter::toProtoAccessListItem)
                .collect(Collectors.toList());
            builder.addAllAccessList(accessList);
        }

        return builder.build();
    }

    /**
     * Convert AccessListEntry POJO to AccessListItem protobuf
     */
    private static Sidecar.AccessListItem toProtoAccessListItem(SidecarApiModels.AccessListEntry pojo) {
        return Sidecar.AccessListItem.newBuilder()
            .setAddress(pojo.getAddress() != null ? pojo.getAddress() : "")
            .addAllStorageKeys(pojo.getStorageKeys() != null ? pojo.getStorageKeys() : new ArrayList<>())
            .build();
    }

    /**
     * Convert List of tx hashes to GetTransactionsRequest protobuf
     */
    public static Sidecar.GetTransactionsRequest toProtoGetTransactionsRequest(List<String> txHashes) {
        return Sidecar.GetTransactionsRequest.newBuilder()
            .addAllTxHashes(txHashes != null ? txHashes : new ArrayList<>())
            .build();
    }

    /**
     * Convert ReorgRequest POJO to protobuf
     */
    public static Sidecar.ReorgRequest toProtoReorgRequest(
            SidecarApiModels.ReorgRequest request) {
        return Sidecar.ReorgRequest.newBuilder()
            .setRemovedTxHash(request.getRemovedTxHash() != null ? request.getRemovedTxHash() : "")
            .build();
    }

    // ==================== RESPONSE CONVERSIONS (Protobuf → POJO) ====================

    /**
     * Convert BasicAck protobuf to SendBlockEnvResponse POJO
     */
    public static SidecarApiModels.SendBlockEnvResponse fromProtoBasicAckToBlockEnvResponse(Sidecar.BasicAck proto) {
        return new SidecarApiModels.SendBlockEnvResponse(
            proto.getAccepted(),
            proto.getAccepted() ? null : proto.getMessage()
        );
    }

    /**
     * Convert BasicAck protobuf to ReorgResponse POJO
     */
    public static SidecarApiModels.ReorgResponse fromProtoBasicAckToReorgResponse(Sidecar.BasicAck proto) {
        return new SidecarApiModels.ReorgResponse(
            proto.getAccepted(),
            proto.getAccepted() ? null : proto.getMessage()
        );
    }

    /**
     * Convert SendTransactionsResponse protobuf to POJO
     */
    public static SidecarApiModels.SendTransactionsResponse fromProtoSendTransactionsResponse(
            Sidecar.SendTransactionsResponse proto) {
        return new SidecarApiModels.SendTransactionsResponse(
            "success", // Status field
            proto.getMessage(),
            proto.getRequestCount()
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

        return new SidecarApiModels.GetTransactionsResponse(
            results,
            new ArrayList<>(proto.getNotFoundList())
        );
    }

    /**
     * Convert TransactionResult protobuf to POJO
     */
    private static SidecarApiModels.TransactionResult fromProtoTransactionResult(
            Sidecar.TransactionResult proto) {
        return new SidecarApiModels.TransactionResult(
            proto.getHash(),
            proto.getStatus(),
            proto.getGasUsed(),
            proto.getError().isEmpty() ? null : proto.getError()
        );
    }
}