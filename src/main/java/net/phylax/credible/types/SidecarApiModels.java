package net.phylax.credible.types;

import net.phylax.credible.utils.ByteUtils;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;

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
    public static class TxEnv {

        private byte txType; // u8 -> byte

        private byte[] caller; // 20 bytes - sender address

        private Long gasLimit; // u64 -> Long

        private byte[] gasPrice; // 32 bytes - U256 big-endian

        private byte[] kind; // 20 bytes or empty for contract creation

        private byte[] value; // 32 bytes - U256 big-endian

        private byte[] data; // calldata bytes (variable length)

        private Long nonce; // u64 -> Long

        private Long chainId; // u64 -> Long

        private List<AccessListEntry> accessList; // AccessList

        private byte[] maxFeePerBlobGas; // 32 bytes - U256 big-endian

        private byte[] gasPriorityFee; // 32 bytes - U256 big-endian

        private List<byte[]> blobHashes = new ArrayList<>();

        private List<AuthorizationListEntry> authorizationList = new ArrayList<>();

        // Constructors
        public TxEnv() {
            this.accessList = new ArrayList<>();
        }

        public TxEnv(byte[] caller, Long gasLimit, byte[] gasPrice,
            byte[] kind, byte[] value, byte[] data,
            Long nonce, Long chainId, List<AccessListEntry> accessList,
            byte txType, byte[] maxFeePerBlobGas,
            byte[] gasPriorityFee, List<byte[]> blobHashes,
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

        public byte[] getGasPrice() { return gasPrice; }
        public void setGasPrice(byte[] gasPrice) { this.gasPrice = gasPrice; }

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

        public byte[] getGasPriorityFee() { return gasPriorityFee; }
        public void setGasPriorityFee(byte[] gasPriorityFee) { this.gasPriorityFee = gasPriorityFee; }

        public List<byte[]> getBlobHashes() { return blobHashes; }
        public void setBlobHashes(List<byte[]> blobHashes) { this.blobHashes = blobHashes; }

        public byte getTxType() { return txType; }
        public void setTxType(byte txType) { this.txType = txType; }

        public byte[] getMaxFeePerBlobGas() { return maxFeePerBlobGas; }
        public void setMaxFeePerBlobGas(byte[] maxFeePerBlobGas) { this.maxFeePerBlobGas = maxFeePerBlobGas; }

        public List<AuthorizationListEntry> getAuthorizationList() { return authorizationList; }
        public void setAuthorizationList(List<AuthorizationListEntry> authorizationList) { this.authorizationList = authorizationList; }

        @Override
        public String toString() {
            return String.format("TxEnv{caller='%s', gasLimit=%d, gasPrice='%s', kind='%s', value='%s', nonce=%d, chainId=%d}",
                    bytesToHex(caller), gasLimit, bytesToHex(gasPrice), bytesToHex(kind), bytesToHex(value), nonce, chainId);
        }
    }
    
    // AuthorizationList Entry - EIP-7702 authorization
    public static class AuthorizationListEntry {
        private Long chainId;

        private Long nonce;

        private byte[] address; // 20 bytes - authorized address

        private byte v;

        private byte[] r; // 32 bytes - signature r

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
    public static class AccessListEntry {
        private byte[] address; // 20 bytes - account address

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

    public static class CommitHead {
        private byte[] lastTxHash; // 32 bytes - last TX hash

        private Integer nTransactions;

        private Long blockNumber;

        private Long selectedIterationId;

        private byte[] blockHash;

        private byte[] parentBeaconBlockRoot;

        private Long timestamp;

        public CommitHead() {}

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
    public static class BlockEnv {
        private Long number;

        private byte[] beneficiary; // 20 bytes - coinbase address

        private Long timestamp;

        private Long gasLimit;

        private Long baseFee;

        private byte[] difficulty; // 32 bytes - U256 big-endian

        private byte[] prevrandao; // 32 bytes - prevrandao hash

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

    public static class NewIteration {
        private Long iterationId;

        private BlockEnv blockEnv;

        public NewIteration() {}

        public NewIteration(Long iterationId, BlockEnv blockEnv) {
            this.blockEnv = blockEnv;
            this.iterationId = iterationId;
        }
        
        public Long getIterationId() { return iterationId; }
        public void setIterationId(Long iterationId) { this.iterationId = iterationId; }

        public BlockEnv getBlockEnv() { return blockEnv; }
        public void setBlockEnv(BlockEnv blockEnv) { this.blockEnv = blockEnv; }
    }

    public static class SendEventsRequest {
        private List<SendEventsRequestItem> events = new ArrayList<>();

        public SendEventsRequest() {}

        public SendEventsRequest(List<SendEventsRequestItem> events) {
            this.events = events;
        }
        
        public List<SendEventsRequestItem> getEvents() { return events; }
        public void setEvents(List<SendEventsRequestItem> events) { this.events = events; }
    }

    public static class SendEventsRequestItem {}

    public static class CommitHeadReqItem extends SendEventsRequestItem {
        private CommitHead commitHead;

        public CommitHeadReqItem() {}

        public CommitHeadReqItem(CommitHead commitHead) {
            this.commitHead = commitHead;
        }

        public CommitHead getCommitHead() { return commitHead; }
        public void setCommitHead(CommitHead commitHead) { this.commitHead = commitHead; }
    }

    public static class NewIterationReqItem extends SendEventsRequestItem {
        private NewIteration newIteration;

        public NewIterationReqItem() {}

        public NewIterationReqItem(NewIteration newIteration) {
            this.newIteration = newIteration;
        }

        public NewIteration getNewIteration() { return newIteration; }
        public void setNewIteration(NewIteration newIteration) { this.newIteration = newIteration; }
    }

    public static class TransactionReqItem extends SendEventsRequestItem {
        private TransactionExecutionPayload transaction;

        public TransactionReqItem() {}

        public TransactionReqItem(TransactionExecutionPayload transaction) {
            this.transaction = transaction;
        }

        public TransactionExecutionPayload getTransaction() { return transaction; }
        public void setTransaction(TransactionExecutionPayload transaction) { this.transaction = transaction; }
    }

    public static class ReorgEventReqItem extends SendEventsRequestItem {
        private ReorgEvent reorg;

        public ReorgEventReqItem() {}

        public ReorgEventReqItem(ReorgEvent reorg) {
            this.reorg = reorg;
        }

        public ReorgEvent getReorg() { return reorg; }
        public void setReorg(ReorgEvent reorg) { this.reorg = reorg; }
    }

    /**
     * Reorg event for streaming - signals a chain reorganization.
     */
    public static class ReorgEvent {
        private TxExecutionId txExecutionId;

        private List<byte[]> txHashes;

        public ReorgEvent() {}

        public ReorgEvent(TxExecutionId txExecutionId, List<byte[]> txHashes) {
            this.txExecutionId = txExecutionId;
            this.txHashes = txHashes;
        }

        public static ReorgEvent fromReorgRequest(ReorgRequest request) {
            return new ReorgEvent(new TxExecutionId(
                request.getBlockNumber(),
                request.getIterationId(),
                request.getTxHash(),
                request.getIndex()
            ), request.txHashes);
        }

        public TxExecutionId getTxExecutionId() { return txExecutionId; }
        public void setTxExecutionId(TxExecutionId txExecutionId) { this.txExecutionId = txExecutionId; }

        public List<byte[]> getTxHashes() { return txHashes; }
        public void setTxHashes(List<byte[]> txHashes) { this.txHashes = txHashes; }
    }

    /**
     * Request model for sendTransactions endpoint
     */
    public static class SendTransactionsRequest {
        private List<TransactionExecutionPayload> transactions;

        public SendTransactionsRequest() {}

        public SendTransactionsRequest(List<TransactionExecutionPayload> transactions) {
            this.transactions = transactions;
        }
        
        public List<TransactionExecutionPayload> getTransactions() { return transactions; }
        public void setTransactions(List<TransactionExecutionPayload> transactions) { this.transactions = transactions; }
    }

    /**
     * Request model for getTransactions endpoint
     */
    public static class GetTransactionsRequest {
        private List<TxExecutionId> txExecutionIds;

        public GetTransactionsRequest() {}

        public GetTransactionsRequest(List<TxExecutionId> txExecutionIds) {
            this.txExecutionIds = txExecutionIds;
        }
        
        public List<TxExecutionId> getTxExecutionIds() { return txExecutionIds; }
        public void setTxExecutionIds(List<TxExecutionId> txExecutionIds) { this.txExecutionIds = txExecutionIds; }
    }

    /**
    * Request model for reorg endpoint
    */
    public static class ReorgRequest {
        private Long blockNumber;

        private Long iterationId;

        private byte[] txHash; // 32 bytes - transaction hash

        private long index;

        private List<byte[]> txHashes;

        public ReorgRequest() {}

        public ReorgRequest(
            Long blockNumber,
            Long iterationId,
            byte[] txHash,
            long index,
            List<byte[]> txHashes
        ) {
            this.blockNumber = blockNumber;
            this.iterationId = iterationId;
            this.txHash = txHash;
            this.index = index;
            this.txHashes = txHashes;
        }

        public static ReorgRequest fromTxExecutionId(TxExecutionId txExecutionId) {
            return new ReorgRequest(
                txExecutionId.getBlockNumber(),
                txExecutionId.getIterationId(),
                txExecutionId.getTxHash(),
                txExecutionId.getIndex(),
                new ArrayList<>()
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
            return new TxExecutionId(blockNumber, iterationId, txHash, index);
        }

        public long getIndex() { return index; }
        public void setIndex(long index) { this.index = index; }

        @Override
        public String toString() {
            return String.format("ReorgRequest{blockNumber=%d, iterationId=%d, txHash='%s', index=%d}",
                blockNumber, iterationId, bytesToHex(txHash), index);
        }
    }

    public static class BlobExcessGasAndPrice {
        private Long excessBlobGas;

        private Long blobGasPrice;

        public BlobExcessGasAndPrice() {}

        public BlobExcessGasAndPrice(Long excessBlobGas, Long blobGasPrice) {
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
    public static class SendTransactionsResponse {
        private String status;

        private String message;

        private Long requestCount;

        public SendTransactionsResponse(String status, String message, Long requestCount) {
            this.status = status;
            this.message = message;
            this.requestCount = requestCount;
        }
        
        public String getStatus() { return status; }
        
        public String getMessage() { return message; }
        public Long getRequestCount() { return requestCount; }
    }

    public static class SendEventsResponse {
        private String status;

        private String message;

        private Long requestCount;

        public SendEventsResponse(String status, String message, Long requestCount) {
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
    public static class GetTransactionsResponse {
        private List<TransactionResult> results = new ArrayList<>();

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

    public static class GetTransactionResponse {
        private TransactionResult result;

        public GetTransactionResponse() {}

        public GetTransactionResponse(TransactionResult result) {
            this.result = result;
        }
        
        public TransactionResult getResult() { return result; }
        public void setResults(TransactionResult result) { this.result = result; }
    
    }

    /**
     * Response model for reorg endpoint
     */
    public static class ReorgResponse {
        private Boolean success;

        private String error;

        public ReorgResponse() {}

        public ReorgResponse(Boolean success, String error) {
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
    public static class TxExecutionId {
        private Long blockNumber;

        private Long iterationId;

        private byte[] txHash; // 32 bytes - transaction hash

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
            return blockNumber.equals(tx.blockNumber)
                && iterationId.equals(tx.iterationId)
                && java.util.Arrays.equals(txHash, tx.txHash)
                && index == tx.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockNumber, iterationId, java.util.Arrays.hashCode(txHash), index);
        }
    }

    /**
     * GetTransactionRequest
     */
    public static class GetTransactionRequest {
        private Long blockNumber;

        private Long iterationId;

        private byte[] txHash; // 32 bytes - transaction hash

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
            return String.format("GetTransactionRequest{blockNumber=%d, iterationId=%d, txHash='%s', index=%d}",
                blockNumber, iterationId, bytesToHex(txHash), index);
        }

        public static GetTransactionRequest fromTxExecutionId(TxExecutionId txExecutionId) {
            return new GetTransactionRequest(txExecutionId.getBlockNumber(), txExecutionId.getIterationId(), txExecutionId.getTxHash(), txExecutionId.getIndex());
        }

        public TxExecutionId toTxExecutionId() {
            return new TxExecutionId(blockNumber, iterationId, txHash, index);
        }
    }

    /**
     * Individual transaction result in getTransactions response
     */
    public static class TransactionResult {
        private TxExecutionId txExecutionId;

        private String status; // "success", "assertion_failed", "failed"

        private Long gasUsed;

        private String error;

        public TransactionResult() {}

        public TransactionResult(TxExecutionId txExecutionId, String status, Long gasUsed, String error) {
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
    public static class TransactionExecutionPayload {
        private TxExecutionId txExecutionId;

        private TxEnv txEnv;

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
