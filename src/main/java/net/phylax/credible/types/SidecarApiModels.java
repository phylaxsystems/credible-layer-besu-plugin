package net.phylax.credible.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import net.phylax.credible.utils.ByteUtils;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

public class SidecarApiModels {

    /**
     * Helper method to convert byte array to hex string for logging/debugging.
     * @deprecated Use {@link ByteUtils#toHex(byte[])} instead
     */
    @Deprecated
    public static String bytesToHex(byte[] bytes) {
        return ByteUtils.toHex(bytes);
    }

    /**
     * Java equivalent of Rust TxEnv struct
     * Updated to match API specification field names.
     * Binary fields (addresses, hashes, values) use byte[] to avoid unnecessary hex string conversions.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TxEnv {

        @JsonIgnore
        private byte txType; // u8 -> byte

        @JsonIgnore
        private byte[] caller; // 20 bytes - sender address

        @JsonIgnore
        private Long gasLimit; // u64 -> Long

        @JsonIgnore
        private Long gasPrice; // u64 -> Long

        @JsonIgnore
        private byte[] kind; // 20 bytes or empty for contract creation

        @JsonIgnore
        private byte[] value; // 32 bytes - U256 big-endian

        @JsonIgnore
        private byte[] data; // calldata bytes (variable length)

        @JsonIgnore
        private Long nonce; // u64 -> Long

        @JsonIgnore
        private Long chainId; // u64 -> Long

        @JsonIgnore
        private List<AccessListEntry> accessList; // AccessList

        @JsonIgnore
        private Long maxFeePerBlobGas = 0L;

        @JsonIgnore
        private Long gasPriorityFee = null;

        @JsonIgnore
        private List<byte[]> blobHashes = new ArrayList<>();

        @JsonIgnore
        private List<AuthorizationListEntry> authorizationList = new ArrayList<>();

        // Constructors
        public TxEnv() {
            this.accessList = new ArrayList<>();
        }

        public TxEnv(byte[] caller, Long gasLimit, Long gasPrice,
            byte[] kind, byte[] value, byte[] data,
            Long nonce, Long chainId, List<AccessListEntry> accessList,
            byte txType, Long maxFeePerBlobGas,
            Long gasPriorityFee, List<byte[]> blobHashes,
            List<AuthorizationListEntry> authorizationList) {
            this.caller = caller;
            this.gasLimit = gasLimit;
            this.gasPrice = gasPrice;
            this.kind = kind;
            this.value = value;
            this.data = data;
            this.nonce = nonce;
            this.chainId = chainId;
            this.accessList = accessList != null ? accessList : new ArrayList<>();
            this.txType = txType;
            this.maxFeePerBlobGas = maxFeePerBlobGas;
            this.gasPriorityFee = gasPriorityFee;
            this.blobHashes = blobHashes != null ? blobHashes : new ArrayList<>();
            this.authorizationList = authorizationList != null ? authorizationList : new ArrayList<>();
        }

        // Getters and Setters
        public byte[] getCaller() { return caller; }
        public void setCaller(byte[] caller) { this.caller = caller; }

        public Long getGasLimit() { return gasLimit; }
        public void setGasLimit(Long gasLimit) { this.gasLimit = gasLimit; }

        public Long getGasPrice() { return gasPrice; }
        public void setGasPrice(Long gasPrice) { this.gasPrice = gasPrice; }

        public byte[] getKind() { return kind; }
        public void setKind(byte[] kind) { this.kind = kind; }

        public byte[] getValue() { return value; }
        public void setValue(byte[] value) { this.value = value; }

        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; }

        public Long getNonce() { return nonce; }
        public void setNonce(Long nonce) { this.nonce = nonce; }

        public Long getChainId() { return chainId; }
        public void setChainId(Long chainId) { this.chainId = chainId; }

        public List<AccessListEntry> getAccessList() { return accessList; }
        public void setAccessList(List<AccessListEntry> accessList) {
            this.accessList = accessList != null ? accessList : new ArrayList<>();
        }

        public Long getGasPriorityFee() { return gasPriorityFee; }
        public void setGasPriorityFee(Long gasPriorityFee) { this.gasPriorityFee = gasPriorityFee; }

        public List<byte[]> getBlobHashes() { return blobHashes; }
        public void setBlobHashes(List<byte[]> blobHashes) { this.blobHashes = blobHashes; }

        public byte getTxType() { return txType; }
        public void setTxType(byte txType) { this.txType = txType; }

        public Long getMaxFeePerBlobGas() { return maxFeePerBlobGas; }
        public void setMaxFeePerBlobGas(Long maxFeePerBlobGas) { this.maxFeePerBlobGas = maxFeePerBlobGas; }

        public List<AuthorizationListEntry> getAuthorizationList() { return authorizationList; }
        public void setAuthorizationList(List<AuthorizationListEntry> authorizationList) { this.authorizationList = authorizationList; }

        @Override
        public String toString() {
            return String.format("TxEnv{caller='%s', gasLimit=%d, gasPrice=%d, kind='%s', value='%s', nonce=%d, chainId=%d}",
                    bytesToHex(caller), gasLimit, gasPrice, bytesToHex(kind), bytesToHex(value), nonce, chainId);
        }
    }
    
    // AuthorizationList Entry - EIP-7702 authorization
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthorizationListEntry {
        @JsonIgnore
        private Long chainId;

        @JsonIgnore
        private Long nonce;

        @JsonIgnore
        private byte[] address; // 20 bytes - authorized address

        @JsonIgnore
        private byte v;

        @JsonIgnore
        private byte[] r; // 32 bytes - signature r

        @JsonIgnore
        private byte[] s; // 32 bytes - signature s

        public AuthorizationListEntry() {}

        public AuthorizationListEntry(byte[] address, byte v, byte[] r,
            byte[] s, Long chainId, Long nonce) {
            this.chainId = chainId;
            this.nonce = nonce;
            this.address = address;
            this.v = v;
            this.r = r;
            this.s = s;
        }

        public byte[] getAddress() { return address; }
        public void setAddress(byte[] address) { this.address = address; }

        public byte getV() { return v; }
        public void setV(byte v) { this.v = v; }

        public byte[] getR() { return r; }
        public void setR(byte[] r) { this.r = r; }

        public byte[] getS() { return s; }
        public void setS(byte[] s) { this.s = s; }

        public Long getChainId() { return chainId; }
        public void setChainId(Long chainId) { this.chainId = chainId; }

        public Long getNonce() { return nonce; }
        public void setNonce(Long nonce) { this.nonce = nonce; }

        @Override
        public String toString() {
            return String.format("AuthorizationListEntry{address='%s', v=%d, r='%s', s='%s', chainId=%d, nonce=%d}",
                    bytesToHex(address), v, bytesToHex(r), bytesToHex(s), chainId, nonce);
        }
    }

    // AccessList Entry - EIP-2930 access list item
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AccessListEntry {
        @JsonIgnore
        private byte[] address; // 20 bytes - account address

        @JsonIgnore
        private List<byte[]> storageKeys; // 32 bytes each - storage slot keys

        public AccessListEntry() {}

        public AccessListEntry(byte[] address, List<byte[]> storageKeys) {
            this.address = address;
            this.storageKeys = storageKeys != null ? storageKeys : new ArrayList<>();
        }

        public byte[] getAddress() { return address; }
        public void setAddress(byte[] address) { this.address = address; }

        public List<byte[]> getStorageKeys() { return storageKeys; }
        public void setStorageKeys(List<byte[]> storageKeys) {
            this.storageKeys = storageKeys != null ? storageKeys : new ArrayList<>();
        }

        @Override
        public String toString() {
            return "AccessListEntry{address='" + bytesToHex(address) + "', storageKeys=" + storageKeys.size() + " keys}";
        }
    }

    // ==================== REQUEST MODELS ====================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CommitHead {
        @JsonIgnore
        private byte[] lastTxHash; // 32 bytes - last TX hash

        @JsonIgnore
        private Integer nTransactions;

        @JsonIgnore
        private Long blockNumber;

        @JsonIgnore
        private Long selectedIterationId;

        @JsonIgnore
        private byte[] blockHash;

        @JsonIgnore
        private byte[] parentBeaconBlockRoot;

        @JsonIgnore
        private Long timestamp;

        public CommitHead() {}

        public CommitHead(
            byte[] lastTxHash,
            Integer nTransactions,
            Long blockNumber,
            Long selectedIterationId
        ) {
            this.lastTxHash = lastTxHash;
            this.nTransactions = nTransactions;
            this.blockNumber = blockNumber;
            this.selectedIterationId = selectedIterationId;
        }

        public CommitHead(
            byte[] lastTxHash,
            Integer nTransactions,
            Long blockNumber,
            Long selectedIterationId,
            byte[] blockHash,
            byte[] parentBeaconBlockRoot,
            Long timestamp
        ) {
            this.lastTxHash = lastTxHash;
            this.nTransactions = nTransactions;
            this.blockNumber = blockNumber;
            this.selectedIterationId = selectedIterationId;
            this.blockHash = blockHash;
            this.parentBeaconBlockRoot = parentBeaconBlockRoot;
            this.timestamp = timestamp;
        }

        public byte[] getLastTxHash() { return lastTxHash; }
        public void setLastTxHash(byte[] lastTxHash) { this.lastTxHash = lastTxHash; }

        public Integer getNTransactions() { return nTransactions; }
        public void setNTransactions(Integer nTransactions) { this.nTransactions = nTransactions; }

        public Long getBlockNumber() { return blockNumber; }
        public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }

        public Long getSelectedIterationId() { return selectedIterationId; }
        public void setSelectedIterationId(Long selectedIterationId) { this.selectedIterationId = selectedIterationId; }

        public byte[] getBlockHash() { return blockHash; }
        public void setBlockHash(byte[] blockHash) { this.blockHash = blockHash; }

        public byte[] getParentBeaconBlockRoot() { return parentBeaconBlockRoot; }
        public void setParentBeaconBlockRoot(byte[] parentBeaconBlockRoot) { this.parentBeaconBlockRoot = parentBeaconBlockRoot; }

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Block environment data
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BlockEnv {
        @JsonIgnore
        private Long number;

        @JsonIgnore
        private byte[] beneficiary; // 20 bytes - coinbase address

        @JsonIgnore
        private Long timestamp;

        @JsonIgnore
        private Long gasLimit;

        @JsonIgnore
        private Long baseFee;

        @JsonIgnore
        private byte[] difficulty; // 32 bytes - U256 big-endian

        @JsonIgnore
        private byte[] prevrandao; // 32 bytes - prevrandao hash

        @JsonIgnore
        private BlobExcessGasAndPrice blobExcessGasAndPrice;

        public BlockEnv() {}

        public BlockEnv(
            Long number,
            byte[] beneficiary,
            Long timestamp,
            Long gasLimit,
            Long baseFee,
            byte[] difficulty,
            byte[] prevrandao,
            BlobExcessGasAndPrice blobExcessGasAndPrice
        ) {
            this.number = number;
            this.beneficiary = beneficiary;
            this.timestamp = timestamp;
            this.gasLimit = gasLimit;
            this.baseFee = baseFee;
            this.difficulty = difficulty;
            this.prevrandao = prevrandao;
            this.blobExcessGasAndPrice = blobExcessGasAndPrice;
        }

        // Getters and setters
        public Long getNumber() { return number; }
        public void setNumber(Long number) { this.number = number; }

        public byte[] getBeneficiary() { return beneficiary; }
        public void setBeneficiary(byte[] beneficiary) { this.beneficiary = beneficiary; }

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

        public Long getGasLimit() { return gasLimit; }
        public void setGasLimit(Long gasLimit) { this.gasLimit = gasLimit; }

        public Long getBaseFee() { return baseFee; }
        public void setBaseFee(Long baseFee) { this.baseFee = baseFee; }

        public byte[] getDifficulty() { return difficulty; }
        public void setDifficulty(byte[] difficulty) { this.difficulty = difficulty; }

        public byte[] getPrevrandao() { return prevrandao; }
        public void setPrevrandao(byte[] prevrandao) { this.prevrandao = prevrandao; }

        public BlobExcessGasAndPrice getBlobExcessGasAndPrice() { return blobExcessGasAndPrice; }
        public void setBlobExcessGasAndPrice(BlobExcessGasAndPrice blobExcessGasAndPrice) {
            this.blobExcessGasAndPrice = blobExcessGasAndPrice;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NewIteration {
        @JsonProperty("iteration_id")
        private Long iterationId;

        @JsonProperty("block_env")
        private BlockEnv blockEnv;
        
        public NewIteration() {}
        
        @JsonCreator
        public NewIteration(@JsonProperty("iteration_id") Long iterationId, @JsonProperty("block_env") BlockEnv blockEnv) {
            this.blockEnv = blockEnv;
            this.iterationId = iterationId;
        }
        
        public Long getIterationId() { return iterationId; }
        public void setIterationId(Long iterationId) { this.iterationId = iterationId; }

        public BlockEnv getBlockEnv() { return blockEnv; }
        public void setBlockEnv(BlockEnv blockEnv) { this.blockEnv = blockEnv; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendEventsRequest {
        @JsonProperty("events")
        private List<SendEventsRequestItem> events = new ArrayList<>();
        
        public SendEventsRequest() {}
        
        @JsonCreator
        public SendEventsRequest(@JsonProperty("events") List<SendEventsRequestItem> events) {
            this.events = events;
        }
        
        public List<SendEventsRequestItem> getEvents() { return events; }
        public void setEvents(List<SendEventsRequestItem> events) { this.events = events; }
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.DEDUCTION
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = CommitHeadReqItem.class),
        @JsonSubTypes.Type(value = NewIterationReqItem.class),
        @JsonSubTypes.Type(value = TransactionReqItem.class),
        @JsonSubTypes.Type(value = ReorgEventReqItem.class)
    })
    public static class SendEventsRequestItem {}

    public static class CommitHeadReqItem extends SendEventsRequestItem {
        @JsonProperty("commit_head")
        private CommitHead commitHead;

        public CommitHeadReqItem() {}

        public CommitHeadReqItem(@JsonProperty("commit_head") CommitHead commitHead) {
            this.commitHead = commitHead;
        }

        public CommitHead getCommitHead() { return commitHead; }
        public void setCommitHead(CommitHead commitHead) { this.commitHead = commitHead; }
    }

    public static class NewIterationReqItem extends SendEventsRequestItem {
        @JsonProperty("new_iteration")
        private NewIteration newIteration;

        public NewIterationReqItem() {}

        public NewIterationReqItem(@JsonProperty("new_iteration") NewIteration newIteration) {
            this.newIteration = newIteration;
        }

        public NewIteration getNewIteration() { return newIteration; }
        public void setNewIteration(NewIteration newIteration) { this.newIteration = newIteration; }
    }

    public static class TransactionReqItem extends SendEventsRequestItem {
        @JsonProperty("transaction")
        private TransactionExecutionPayload transaction;

        public TransactionReqItem() {}

        public TransactionReqItem(@JsonProperty("transaction") TransactionExecutionPayload transaction) {
            this.transaction = transaction;
        }

        public TransactionExecutionPayload getTransaction() { return transaction; }
        public void setTransaction(TransactionExecutionPayload transaction) { this.transaction = transaction; }
    }

    public static class ReorgEventReqItem extends SendEventsRequestItem {
        @JsonProperty("reorg")
        private ReorgEvent reorg;

        public ReorgEventReqItem() {}

        public ReorgEventReqItem(@JsonProperty("reorg") ReorgEvent reorg) {
            this.reorg = reorg;
        }

        public ReorgEvent getReorg() { return reorg; }
        public void setReorg(ReorgEvent reorg) { this.reorg = reorg; }
    }

    /**
     * Reorg event for streaming - signals a chain reorganization.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReorgEvent {
        @JsonProperty("tx_execution_id")
        private TxExecutionId txExecutionId;

        public ReorgEvent() {}

        @JsonCreator
        public ReorgEvent(@JsonProperty("tx_execution_id") TxExecutionId txExecutionId) {
            this.txExecutionId = txExecutionId;
        }

        public static ReorgEvent fromReorgRequest(ReorgRequest request) {
            return new ReorgEvent(new TxExecutionId(
                request.getBlockNumber(),
                request.getIterationId(),
                request.getTxHash(),
                request.getIndex()
            ));
        }

        public TxExecutionId getTxExecutionId() { return txExecutionId; }
        public void setTxExecutionId(TxExecutionId txExecutionId) { this.txExecutionId = txExecutionId; }
    }

    /**
     * Request model for sendTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendTransactionsRequest {
        @JsonProperty("transactions")
        private List<TransactionExecutionPayload> transactions;
        
        public SendTransactionsRequest() {}
        
        @JsonCreator
        public SendTransactionsRequest(@JsonProperty("transactions") List<TransactionExecutionPayload> transactions) {
            this.transactions = transactions;
        }
        
        public List<TransactionExecutionPayload> getTransactions() { return transactions; }
        public void setTransactions(List<TransactionExecutionPayload> transactions) { this.transactions = transactions; }
    }

    /**
     * Request model for getTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetTransactionsRequest {
        @JsonProperty("tx_execution_ids")
        private List<TxExecutionId> txExecutionIds;
        
        public GetTransactionsRequest() {}
        
        @JsonCreator
        public GetTransactionsRequest(@JsonProperty("tx_execution_ids") List<TxExecutionId> txExecutionIds) {
            this.txExecutionIds = txExecutionIds;
        }
        
        public List<TxExecutionId> getTxExecutionIds() { return txExecutionIds; }
        public void setTxExecutionIds(List<TxExecutionId> txExecutionIds) { this.txExecutionIds = txExecutionIds; }
    }

    /**
    * Request model for reorg endpoint
    */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReorgRequest {
        @JsonIgnore
        private Long blockNumber;

        @JsonIgnore
        private Long iterationId;

        @JsonIgnore
        private byte[] txHash; // 32 bytes - transaction hash

        @JsonIgnore
        private long index;

        public ReorgRequest() {}

        public ReorgRequest(
            Long blockNumber,
            Long iterationId,
            byte[] txHash,
            long index
        ) {
            this.blockNumber = blockNumber;
            this.iterationId = iterationId;
            this.txHash = txHash;
            this.index = index;
        }

        public static ReorgRequest fromTxExecutionId(TxExecutionId txExecutionId) {
            return new ReorgRequest(
                txExecutionId.getBlockNumber(),
                txExecutionId.getIterationId(),
                txExecutionId.getTxHash(),
                txExecutionId.getIndex()
            );
        }

        public Long getBlockNumber() {
            return blockNumber;
        }

        public void setBlockNumber(Long blockNumber) {
            this.blockNumber = blockNumber;
        }

        public Long getIterationId() {
            return iterationId;
        }

        public void setIterationId(Long iterationId) {
            this.iterationId = iterationId;
        }

        public byte[] getTxHash() {
            return txHash;
        }

        public void setTxHash(byte[] txHash) {
            this.txHash = txHash;
        }

        public TxExecutionId toTxExecutionId() {
            return new TxExecutionId(blockNumber, iterationId, txHash, 0);
        }

        public long getIndex() { return index; }
        public void setIndex(long index) { this.index = index; }
    }

    /**
     * Request model for sendBlockEnv endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendBlockEnvRequest {
        @JsonProperty("block_env")
        private BlockEnv blockEnv;

        @JsonProperty("last_tx_hash")
        private String lastTxHash;

        @JsonProperty("n_transactions")
        private Integer nTransactions;

        @JsonProperty("selected_iteration_id")
        private Long selectedIterationId;

        public SendBlockEnvRequest() {}

        @JsonCreator
        public SendBlockEnvRequest(
            @JsonProperty("block_env") BlockEnv blockEnv,
            @JsonProperty("last_tx_hash") String lastTxHash,
            @JsonProperty("n_transactions") Integer nTransactions,
            @JsonProperty("selected_iteration_id") Long selectedIterationId
        ) {
            this.blockEnv = blockEnv;
            this.lastTxHash = lastTxHash;
            this.nTransactions = nTransactions;
            this.selectedIterationId = selectedIterationId;
        }

        // Getters and setters
        public BlockEnv getBlockEnv() { return blockEnv; }
        public void setBlockEnv(BlockEnv blockEnv) { this.blockEnv = blockEnv; }

        public String getLastTxHash() { return lastTxHash; }
        public void setLastTxHash(String lastTxHash) { this.lastTxHash = lastTxHash; }

        public Integer getNTransactions() { return nTransactions; }
        public void setNTransactions(Integer nTransactions) { this.nTransactions = nTransactions; }

        public Long getSelectedIterationId() { return selectedIterationId; }
        public void setSelectedIterationId(Long selectedIterationId) { this.selectedIterationId = selectedIterationId; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BlobExcessGasAndPrice {
        @JsonProperty("excess_blob_gas")
        private Long excessBlobGas;
        
        @JsonProperty("blob_gasprice")
        private Long blobGasPrice;
        
        public BlobExcessGasAndPrice() {}
        
        @JsonCreator
        public BlobExcessGasAndPrice(@JsonProperty("excess_blob_gas") Long excessBlobGas, @JsonProperty("blob_gasprice") Long blobGasPrice) {
            this.excessBlobGas = excessBlobGas;
            this.blobGasPrice = blobGasPrice;
        }
        
        public Long getExcessBlobGas() { return excessBlobGas; }
        public void setExcessBlobGas(Long excessBlobGas) { this.excessBlobGas = excessBlobGas; }
        
        public Long getBlobGasPrice() { return blobGasPrice; }
        public void setBlobGasPrice(Long blobGasPrice) { this.blobGasPrice = blobGasPrice; }
        
        @Override
        public String toString() {
            return String.format("BlobExcessGasAndPrice{excessBlobGas=%d, blobGasPrice=%d}", excessBlobGas, blobGasPrice);
        }
    }

    // ==================== RESPONSE MODELS ====================

    /**
     * Response model for sendTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendTransactionsResponse {
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("message")
        private String message;

        @JsonProperty("request_count")
        private Long requestCount;
        
        @JsonCreator
        public SendTransactionsResponse(@JsonProperty("status") String status, @JsonProperty("message") String message,
            @JsonProperty("request_count") Long requestCount) {
            this.status = status;
            this.message = message;
            this.requestCount = requestCount;
        }
        
        public String getStatus() { return status; }
        
        public String getMessage() { return message; }
        public Long getRequestCount() { return requestCount; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendEventsResponse {
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("message")
        private String message;

        @JsonProperty("request_count")
        private Long requestCount;
        
        @JsonCreator
        public SendEventsResponse(@JsonProperty("status") String status, @JsonProperty("message") String message,
            @JsonProperty("request_count") Long requestCount) {
            this.status = status;
            this.message = message;
            this.requestCount = requestCount;
        }
        
        public String getStatus() { return status; }
        
        public String getMessage() { return message; }
        public Long getRequestCount() { return requestCount; }
    }

    /**
     * Response model for getTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetTransactionsResponse {
        @JsonIgnore
        private List<TransactionResult> results = new ArrayList<>();

        @JsonIgnore
        private List<byte[]> notFound = new ArrayList<>(); // 32-byte hashes not found

        public GetTransactionsResponse() {}

        public GetTransactionsResponse(List<TransactionResult> results, List<byte[]> notFound) {
            this.results = results;
            this.notFound = notFound;
        }

        public List<TransactionResult> getResults() { return results; }
        public void setResults(List<TransactionResult> results) { this.results = results; }

        public List<byte[]> getNotFound() { return notFound; }
        public void setNotFound(List<byte[]> notFound) { this.notFound = notFound; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetTransactionResponse {
        @JsonProperty("result")
        private TransactionResult result;

        public GetTransactionResponse() {}
        
        @JsonCreator
        public GetTransactionResponse(@JsonProperty("result") TransactionResult result) {
            this.result = result;
        }
        
        public TransactionResult getResult() { return result; }
        public void setResults(TransactionResult result) { this.result = result; }
    
    }

    /**
     * Response model for sendBlockEnv endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendBlockEnvResponse {
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("request_count")
        private Long requestCount;

        @JsonProperty("message")
        private String message;
        
        public SendBlockEnvResponse() {}

        @JsonCreator
        public SendBlockEnvResponse(@JsonProperty("status") String status,
            @JsonProperty("request_count") Long requestCount,
            @JsonProperty("message") String message
        ) {
            this.status = status;
            this.message = message;
            this.requestCount = requestCount;
        }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Long getRequestCount() { return requestCount; }
        public void setRequestCount(Long requestCount) { this.requestCount = requestCount; }

        @JsonIgnore
        public boolean getSuccess() {
            return "accepted".equalsIgnoreCase(status);
        }
    }

    /**
     * Response model for sendBlockEnv endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReorgResponse {
        @JsonProperty("success")
        private Boolean success;

        @JsonProperty("error")
        private String error;

        public ReorgResponse() {}

        @JsonCreator
        public ReorgResponse(@JsonProperty("success") Boolean success, @JsonProperty("error") String error) {
            this.success = success;
            this.error = error;
        }

        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    // ==================== NESTED MODELS ====================

    /**
     * Transaction execution identifier
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TxExecutionId {
        @JsonIgnore
        private Long blockNumber;

        @JsonIgnore
        private Long iterationId;

        @JsonIgnore
        private byte[] txHash; // 32 bytes - transaction hash

        @JsonIgnore
        private long index;

        public TxExecutionId() {}

        public TxExecutionId(
            Long blockNumber,
            Long iterationId,
            byte[] txHash,
            long index
        ) {
            this.blockNumber = blockNumber;
            this.iterationId = iterationId;
            this.txHash = txHash;
            this.index = index;
        }

        public Long getBlockNumber() { return blockNumber; }
        public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }

        public Long getIterationId() { return iterationId; }
        public void setIterationId(Long iterationId) { this.iterationId = iterationId; }

        public byte[] getTxHash() { return txHash; }
        public void setTxHash(byte[] txHash) { this.txHash = txHash; }

        public long getIndex() { return index; }
        public void setIndex(long index) { this.index = index; }

        @Override
        public String toString() {
            return String.format("TxExecutionId{blockNumber=%d, iterationId=%d, txHash='%s', index=%d}",
                blockNumber, iterationId, bytesToHex(txHash), index);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TxExecutionId tx = (TxExecutionId) o;
            return blockNumber.equals(tx.blockNumber) && iterationId.equals(tx.iterationId) && java.util.Arrays.equals(txHash, tx.txHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockNumber, iterationId, java.util.Arrays.hashCode(txHash));
        }
    }

    /**
     * GetTransactionRequest
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetTransactionRequest {
        @JsonIgnore
        private Long blockNumber;

        @JsonIgnore
        private Long iterationId;

        @JsonIgnore
        private byte[] txHash; // 32 bytes - transaction hash

        @JsonIgnore
        private long index;

        public GetTransactionRequest() {}

        public GetTransactionRequest(
            Long blockNumber,
            Long iterationId,
            byte[] txHash,
            long index
        ) {
            this.blockNumber = blockNumber;
            this.iterationId = iterationId;
            this.txHash = txHash;
            this.index = index;
        }

        public Long getBlockNumber() { return blockNumber; }
        public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }

        public Long getIterationId() { return iterationId; }
        public void setIterationId(Long iterationId) { this.iterationId = iterationId; }

        public byte[] getTxHash() { return txHash; }
        public void setTxHash(byte[] txHash) { this.txHash = txHash; }

        public long getIndex() { return index; }
        public void setIndex(long index) { this.index = index; }

        @Override
        public String toString() {
            return String.format("GetTransactionRequest{blockNumber=%d, iterationId=%d, txHash='%s'}",
                blockNumber, iterationId, bytesToHex(txHash));
        }

        public static GetTransactionRequest fromTxExecutionId(TxExecutionId txExecutionId) {
            return new GetTransactionRequest(txExecutionId.getBlockNumber(), txExecutionId.getIterationId(), txExecutionId.getTxHash(), txExecutionId.getIndex());
        }

        public TxExecutionId toTxExecutionId() {
            return new TxExecutionId(blockNumber, iterationId, txHash, 0);
        }
    }

    /**
     * Individual transaction result in getTransactions response
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionResult {
        @JsonProperty("tx_execution_id")
        private TxExecutionId txExecutionId;
        
        @JsonProperty("status")
        private String status; // "success", "assertion_failed", "failed"
        
        @JsonProperty("gas_used")
        private Long gasUsed;
        
        @JsonProperty("error")
        private String error;
        
        public TransactionResult() {}
        
        @JsonCreator
        public TransactionResult(@JsonProperty("tx_execution_id") TxExecutionId txExecutionId, @JsonProperty("status") String status,
            @JsonProperty("gas_used") Long gasUsed, @JsonProperty("error") String error) {
            this.txExecutionId = txExecutionId;
            this.status = status;
            this.gasUsed = gasUsed;
            this.error = error;
        }
        
        public TxExecutionId getTxExecutionId() { return txExecutionId; }
        public void setTxExecutionId(TxExecutionId txExecutionId) { this.txExecutionId = txExecutionId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Long getGasUsed() { return gasUsed; }
        public void setGasUsed(Long gasUsed) { this.gasUsed = gasUsed; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    /**
     * Transaction payload for sidecar processing
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionExecutionPayload {
        @JsonIgnore
        private TxExecutionId txExecutionId;

        @JsonIgnore
        private TxEnv txEnv;

        @JsonIgnore
        private byte[] prevTxHash; // 32 bytes - previous TX hash for ordering

        public TransactionExecutionPayload(
            TxExecutionId txExecutionId,
            TxEnv txEnv,
            byte[] prevTxHash
        ) {
            this.txExecutionId = txExecutionId;
            this.txEnv = txEnv;
            this.prevTxHash = prevTxHash;
        }

        public TxExecutionId getTxExecutionId() { return txExecutionId; }
        public void setTxExecutionId(TxExecutionId txExecutionId) { this.txExecutionId = txExecutionId; }

        public TxEnv getTxEnv() { return txEnv; }
        public void setTxEnv(TxEnv txEnv) { this.txEnv = txEnv; }

        public byte[] getPrevTxHash() { return prevTxHash; }
        public void setPrevTxHash(byte[] prevTxHash) { this.prevTxHash = prevTxHash; }
    }

    // ==================== ENUMS & CONSTANTS ====================

    /**
     * Transaction status constants
     */
    public static class TransactionStatus {
        public static final String SUCCESS = "success";
        public static final String ASSERTION_FAILED = "assertion_failed";
        public static final String FAILED = "failed";
        public static final String REVERTED = "reverted";
        public static final String HALTED = "halted";

        private TransactionStatus() {} // Utility class
    }

    /**
     * JSON-RPC method names
     */
    public static class CredibleLayerMethods {
        public static final String SEND_TRANSACTIONS = "sendTransactions";
        public static final String GET_TRANSACTIONS = "getTransactions";
        public static final String GET_TRANSACTION = "getTransaction";
        public static final String SEND_BLOCK_ENV = "sendBlockEnv";
        public static final String SEND_REORG = "reorg";
        public static final String SEND_EVENTS = "sendEvents";
        public static final String NEW_ITERATION = "newIteration";

        private CredibleLayerMethods() {} // Utility class
    }
}
