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
            .setBlockEnv(toProtoBlockEnv(request.getBlockEnv()))
            .setNTransactions(request.getNTransactions())
            .setSelectedIterationId(request.getSelectedIterationId());

        if (request.getLastTxHash() != null && !request.getLastTxHash().isEmpty()) {
            builder.setLastTxHash(request.getLastTxHash());
        }

        return builder.build();
    }

    /**
     * Convert BlockEnv POJO to BlockEnv protobuf
     */
    private static Sidecar.BlockEnv toProtoBlockEnv(SidecarApiModels.BlockEnv blockEnv) {
        if (blockEnv == null) {
            return Sidecar.BlockEnv.getDefaultInstance();
        }

        Sidecar.BlockEnv.Builder builder = Sidecar.BlockEnv.newBuilder()
            .setNumber(blockEnv.getNumber())
            .setBeneficiary(blockEnv.getBeneficiary())
            .setTimestamp(blockEnv.getTimestamp())
            .setGasLimit(blockEnv.getGasLimit())
            .setBasefee(blockEnv.getBaseFee())
            .setDifficulty(blockEnv.getDifficulty());

        if (blockEnv.getPrevrandao() != null) {
            builder.setPrevrandao(blockEnv.getPrevrandao());
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
            .setBlobGasprice(String.valueOf(pojo.getBlobGasPrice()))
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
     * Convert TransactionExecutionPayload POJO to Transaction protobuf
     */
    private static Sidecar.Transaction toProtoTransaction(SidecarApiModels.TransactionExecutionPayload pojo) {
        return Sidecar.Transaction.newBuilder()
            .setTxExecutionId(toProtoTxExecutionId(pojo.getTxExecutionId()))
            .setTxEnv(toProtoTransactionEnv(pojo.getTxEnv()))
            .build();
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
            .setTxHash(pojo.getTxHash())
            .build();
    }

    /**
     * Convert TxEnv POJO to TransactionEnv protobuf
     */
    private static Sidecar.TransactionEnv toProtoTransactionEnv(SidecarApiModels.TxEnv pojo) {
        if (pojo == null) {
            return Sidecar.TransactionEnv.getDefaultInstance();
        }

        Sidecar.TransactionEnv.Builder builder = Sidecar.TransactionEnv.newBuilder()
            .setTxType(Byte.toUnsignedInt(pojo.getTxType()))
            .setCaller(pojo.getCaller())
            .setGasLimit(pojo.getGasLimit())
            .setGasPrice(String.valueOf(pojo.getGasPrice()))
            .setKind(pojo.getKind())
            .setValue(pojo.getValue())
            .setData(pojo.getData())
            .setNonce(pojo.getNonce())
            .setMaxFeePerBlobGas(String.valueOf(pojo.getMaxFeePerBlobGas()));

        if (pojo.getChainId() != null) {
            builder.setChainId(pojo.getChainId());
        }

        if (pojo.getGasPriorityFee() != null) {
            builder.setGasPriorityFee(String.valueOf(pojo.getGasPriorityFee()));
        }

        if (pojo.getBlobHashes() != null && !pojo.getBlobHashes().isEmpty()) {
            builder.addAllBlobHashes(pojo.getBlobHashes());
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
        return Sidecar.AccessListItem.newBuilder()
            .setAddress(pojo.getAddress() != null ? pojo.getAddress() : "")
            .addAllStorageKeys(pojo.getStorageKeys() != null ? pojo.getStorageKeys() : new ArrayList<>())
            .build();
    }

    private static Sidecar.Authorization toProtoAuthorization(SidecarApiModels.AuthorizationListEntry pojo) {
        Sidecar.Authorization.Builder builder = Sidecar.Authorization.newBuilder()
            .setAddress(pojo.getAddress() != null ? pojo.getAddress() : "")
            .setYParity(String.valueOf(Byte.toUnsignedInt(pojo.getV())))
            .setR(pojo.getR() != null ? pojo.getR() : "")
            .setS(pojo.getS() != null ? pojo.getS() : "");

        if (pojo.getChainId() != null) {
            builder.setChainId(String.valueOf(pojo.getChainId()));
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
            .addAllTxExecutionId(protoIds)
            .build();
    }

    /**
     * Convert TxExecutionId to GetTransactionRequest protobuf
     */
    public static Sidecar.GetTransactionRequest toProtoGetTransactionRequest(SidecarApiModels.GetTransactionRequest request) {
        var txExecId = Sidecar.TxExecutionId.newBuilder()
            .setBlockNumber(request.getBlockNumber())
            .setIterationId(request.getIterationId())
            .setTxHash(request.getTxHash());
        return Sidecar.GetTransactionRequest.newBuilder()
            .setTxExecutionId(txExecId)
            .build();
    }

    /**
     * Convert ReorgRequest POJO to protobuf
     */
    public static Sidecar.ReorgRequest toProtoReorgRequest(
            SidecarApiModels.ReorgRequest request) {
        Sidecar.TxExecutionId.Builder txExecIdBuilder = Sidecar.TxExecutionId.newBuilder()
            .setBlockNumber(request.getBlockNumber())
            .setIterationId(request.getIterationId())
            .setTxHash(request.getTxHash());

        return Sidecar.ReorgRequest.newBuilder()
            .setTxExecutionId(txExecIdBuilder.build())
            .build();
    }

    /**
     * Convert SendEventsRequest POJO to SendEvents protobuf
     */
    public static Sidecar.SendEvents toProtoSendEvents(SidecarApiModels.SendEventsRequest request) {
        List<Sidecar.SendEvents.Event> events = request.getEvents().stream()
            .map(GrpcModelConverter::toProtoSendEventsEvent)
            .collect(Collectors.toList());

        return Sidecar.SendEvents.newBuilder()
            .addAllEvents(events)
            .build();
    }

    /**
     * Convert SendEventsRequestItem POJO to SendEvents.Event protobuf
     */
    private static Sidecar.SendEvents.Event toProtoSendEventsEvent(SidecarApiModels.SendEventsRequestItem item) {
        Sidecar.SendEvents.Event.Builder builder = Sidecar.SendEvents.Event.newBuilder();

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
        }

        return builder.build();
    }

    /**
     * Convert CommitHead POJO to protobuf
     */
    private static Sidecar.CommitHead toProtoCommitHead(SidecarApiModels.CommitHead pojo) {
        Sidecar.CommitHead.Builder builder = Sidecar.CommitHead.newBuilder()
            .setBlockNumber(pojo.getBlockNumber())
            .setNTransactions(pojo.getNTransactions());

        if (pojo.getLastTxHash() != null && !pojo.getLastTxHash().isEmpty()) {
            builder.setLastTxHash(pojo.getLastTxHash());
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
     * Convert BasicAck protobuf to SendBlockEnvResponse POJO
     */
    public static SidecarApiModels.SendBlockEnvResponse fromProtoBasicAckToBlockEnvResponse(Sidecar.BasicAck proto) {
        return new SidecarApiModels.SendBlockEnvResponse(
            proto.getAccepted() ? "accepted" : "failed",
            proto.getAccepted() ? 1L : 0L,
            proto.getMessage().isEmpty() ? null : proto.getMessage()
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
     * Convert BasicAck protobuf to SendEventsResponse POJO
     */
    public static SidecarApiModels.SendEventsResponse fromProtoBasicAckToSendEventsResponse(Sidecar.BasicAck proto) {
        return new SidecarApiModels.SendEventsResponse(
            proto.getAccepted() ? "accepted" : "failed",
            proto.getMessage().isEmpty() ? null : proto.getMessage(),
            proto.getAccepted() ? 1L : 0L
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
            proto.getAcceptedCount()
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
    private static SidecarApiModels.TransactionResult fromProtoTransactionResult(
            Sidecar.TransactionResult proto) {
        SidecarApiModels.TxExecutionId txExecutionId = fromProtoTxExecutionId(proto.getTxExecutionId());
        return new SidecarApiModels.TransactionResult(
            txExecutionId,
            proto.getStatus(),
            proto.getGasUsed(),
            proto.getError().isEmpty() ? null : proto.getError()
        );
    }

    /**
     * Convert TxExecutionId protobuf to POJO
     */
    private static SidecarApiModels.TxExecutionId fromProtoTxExecutionId(
            Sidecar.TxExecutionId proto) {
        return new SidecarApiModels.TxExecutionId(
            proto.getBlockNumber(),
            proto.getIterationId(),
            proto.getTxHash()
        );
    }
}
