package net.phylax.credible.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.ArrayList;

public class SidecarApiModels {
    /**
     * Java equivalent of Rust TxEnv struct
     * Updated to match API specification field names
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TxEnv {
        
        @JsonProperty("tx_type")
        private byte txType; // u8 -> byte

        @JsonProperty("caller")
        private String caller; // Address as hex string
        
        @JsonProperty("gas_limit")
        private Long gasLimit; // u64 -> Long
        
        @JsonProperty("gas_price")
        private Long gasPrice; // u64 -> Long
        
        @JsonProperty("kind")
        private String kind; // Address as hex string (null for contract creation)
        
        @JsonProperty("value")
        private String value; // Hex string (e.g., "0x0", "0x1bc16d674ec80000")
        
        @JsonProperty("data")
        private String data; // Bytes as hex string
        
        @JsonProperty("nonce")
        private Long nonce; // u64 -> Long
        
        @JsonProperty("chain_id")
        private Long chainId; // u64 -> Long
        
        @JsonProperty("access_list")
        private List<AccessListEntry> accessList; // AccessList

        @JsonProperty("max_fee_per_blob_gas")
        private Long maxFeePerBlobGas = 0L;

        @JsonProperty("gas_priority_fee")
        private Long gasPriorityFee = null;

        @JsonProperty("blob_hashes")
        private List<String> blobHashes = new ArrayList<>();

        @JsonProperty("authorization_list")
        private List<AuthorizationListEntry> authorizationList = new ArrayList<>();
        
        // Constructors
        public TxEnv() {
            this.accessList = new ArrayList<>();
        }
        
        @JsonCreator
        public TxEnv(@JsonProperty("caller") String caller, @JsonProperty("gas_limit") Long gasLimit, @JsonProperty("gas_price") Long gasPrice,
            @JsonProperty("transact_to") String kind, @JsonProperty("value") String value, @JsonProperty("data") String data,
            @JsonProperty("nonce") Long nonce, @JsonProperty("chain_id") Long chainId, @JsonProperty("access_list") List<AccessListEntry> accessList,
            @JsonProperty("tx_type") byte txType, @JsonProperty("max_fee_per_blob_gas") Long maxFeePerBlobGas,
            @JsonProperty("gas_priority_fee") Long gasPriorityFee, @JsonProperty("blob_hashes") List<String> blobHashes,
            @JsonProperty("authorization_list") List<AuthorizationListEntry> authorizationList) {
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
        public String getCaller() { return caller; }
        public void setCaller(String caller) { this.caller = caller; }
        
        public Long getGasLimit() { return gasLimit; }
        public void setGasLimit(Long gasLimit) { this.gasLimit = gasLimit; }
        
        public Long getGasPrice() { return gasPrice; }
        public void setGasPrice(Long gasPrice) { this.gasPrice = gasPrice; }
        
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
        
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

        public List<String> getBlobHashes() { return blobHashes; }
        public void setBlobHashes(List<String> blobHashes) { this.blobHashes = blobHashes; }
        
        public byte getTxType() { return txType; }
        public void setTxType(byte txType) { this.txType = txType; }

        public Long getMaxFeePerBlobGas() { return maxFeePerBlobGas; }
        public void setMaxFeePerBlobGas(Long maxFeePerBlobGas) { this.maxFeePerBlobGas = maxFeePerBlobGas; }

        public List<AuthorizationListEntry> getAuthorizationList() { return authorizationList; }
        public void setAuthorizationList(List<AuthorizationListEntry> authorizationList) { this.authorizationList = authorizationList; }

        @Override
        public String toString() {
            return String.format("TxEnv{caller='%s', gasLimit=%d, gasPrice='%s', kind='%s', value='%s', data='%s', nonce=%d, chainId=%d}",
                    caller, gasLimit, gasPrice, kind, value, data, nonce, chainId);
        }
    }
    
    // AuthorizationList Entry
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthorizationListEntry {
        @JsonProperty("chain_id")
        private Long chainId;

        @JsonProperty("nonce")
        private Long nonce;

        @JsonProperty("address")
        private String address;
        
        @JsonProperty("v")
        private byte v;

        @JsonProperty("r")
        private String r;

        @JsonProperty("s")
        private String s;
        
        public AuthorizationListEntry() {}
        
        @JsonCreator
        public AuthorizationListEntry(@JsonProperty("address") String address, @JsonProperty("v") byte v, @JsonProperty("r") String r,
            @JsonProperty("s") String s, @JsonProperty("chain_id") Long chainId, @JsonProperty("nonce") Long nonce) {
            this.chainId = chainId;
            this.nonce = nonce;
            this.address = address;
            this.v = v;
            this.r = r;
            this.s = s;
        }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public byte getV() { return v; }
        public void setV(byte v) { this.v = v; }
        
        public String getR() { return r; }
        public void setR(String r) { this.r = r; }
        
        public String getS() { return s; }
        public void setS(String s) { this.s = s; }
        
        public Long getChainId() { return chainId; }
        public void setChainId(Long chainId) { this.chainId = chainId; }
        
        public Long getNonce() { return nonce; }
        public void setNonce(Long nonce) { this.nonce = nonce; }
        
        @Override
        public String toString() {
            return String.format("AuthorizationListEntry{address='%s', v=%d, r='%s', s='%s', chainId=%d, nonce=%d}",
                    address, v, r, s, chainId, nonce);
        }
    }

    // AccessList Entry
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AccessListEntry {
        @JsonProperty("address")
        private String address;
        
        @JsonProperty("storage_keys")
        private List<String> storageKeys;
        
        public AccessListEntry() {}
        
        @JsonCreator
        public AccessListEntry(@JsonProperty("address") String address, @JsonProperty("storage_keys") List<String> storageKeys) {
            this.address = address;
            this.storageKeys = storageKeys != null ? storageKeys : new ArrayList<>();
        }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public List<String> getStorageKeys() { return storageKeys; }
        public void setStorageKeys(List<String> storageKeys) { 
            this.storageKeys = storageKeys != null ? storageKeys : new ArrayList<>(); 
        }
        
        @Override
        public String toString() { 
            return "AccessListEntry{address='" + address + "', storageKeys=" + storageKeys + "}"; 
        }
    }

    // ==================== REQUEST MODELS ====================

    /**
     * Block environment data
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BlockEnv {
        @JsonProperty("number")
        private Long number;

        @JsonProperty("beneficiary")
        private String beneficiary;

        @JsonProperty("timestamp")
        private Long timestamp;

        @JsonProperty("gas_limit")
        private Long gasLimit;

        @JsonProperty("basefee")
        private Long baseFee;

        @JsonProperty("difficulty")
        private String difficulty;

        @JsonProperty("prevrandao")
        private String prevrandao;

        @JsonProperty("blob_excess_gas_and_price")
        private BlobExcessGasAndPrice blobExcessGasAndPrice;

        public BlockEnv() {}

        @JsonCreator
        public BlockEnv(
            @JsonProperty("number") Long number,
            @JsonProperty("beneficiary") String beneficiary,
            @JsonProperty("timestamp") Long timestamp,
            @JsonProperty("gas_limit") Long gasLimit,
            @JsonProperty("basefee") Long baseFee,
            @JsonProperty("difficulty") String difficulty,
            @JsonProperty("prevrandao") String prevrandao,
            @JsonProperty("blob_excess_gas_and_price") BlobExcessGasAndPrice blobExcessGasAndPrice
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

        public String getBeneficiary() { return beneficiary; }
        public void setBeneficiary(String beneficiary) { this.beneficiary = beneficiary; }

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

        public Long getGasLimit() { return gasLimit; }
        public void setGasLimit(Long gasLimit) { this.gasLimit = gasLimit; }

        public Long getBaseFee() { return baseFee; }
        public void setBaseFee(Long baseFee) { this.baseFee = baseFee; }

        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

        public String getPrevrandao() { return prevrandao; }
        public void setPrevrandao(String prevrandao) { this.prevrandao = prevrandao; }

        public BlobExcessGasAndPrice getBlobExcessGasAndPrice() { return blobExcessGasAndPrice; }
        public void setBlobExcessGasAndPrice(BlobExcessGasAndPrice blobExcessGasAndPrice) {
            this.blobExcessGasAndPrice = blobExcessGasAndPrice;
        }
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
        @JsonProperty("block_number")
        private Long blockNumber;

        @JsonProperty("iteration_id")
        private Long iterationId;

        @JsonProperty("tx_hash")
        private String txHash;

        public ReorgRequest() {}

        @JsonCreator
        public ReorgRequest(
            @JsonProperty("block_number") Long blockNumber,
            @JsonProperty("iteration_id") Long iterationId,
            @JsonProperty("tx_hash") String txHash
        ) {
            this.blockNumber = blockNumber;
            this.iterationId = iterationId;
            this.txHash = txHash;
        }

        public static ReorgRequest fromTxExecutionId(TxExecutionId txExecutionId) {
            return new ReorgRequest(
                txExecutionId.getBlockNumber(),
                txExecutionId.getIterationId(),
                txExecutionId.getTxHash()
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

        public String getTxHash() {
            return txHash;
        }

        public void setTxHash(String txHash) {
            this.txHash = txHash;
        }
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

    /**
     * Response model for getTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetTransactionsResponse {
        @JsonProperty("results")
        private List<TransactionResult> results = new ArrayList<>();
        
        @JsonProperty("not_found")
        private List<String> notFound = new ArrayList<>();
        
        public GetTransactionsResponse() {}
        
        @JsonCreator
        public GetTransactionsResponse(@JsonProperty("results") List<TransactionResult> results, @JsonProperty("not_found") List<String> notFound) {
            this.results = results;
            this.notFound = notFound;
        }
        
        public List<TransactionResult> getResults() { return results; }
        public void setResults(List<TransactionResult> results) { this.results = results; }
        
        public List<String> getNotFound() { return notFound; }
        public void setNotFound(List<String> notFound) { this.notFound = notFound; }
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
        @JsonProperty("block_number")
        private Long blockNumber;

        @JsonProperty("iteration_id")
        private Long iterationId;

        @JsonProperty("tx_hash")
        private String txHash;

        public TxExecutionId() {}

        @JsonCreator
        public TxExecutionId(
            @JsonProperty("block_number") Long blockNumber,
            @JsonProperty("iteration_id") Long iterationId,
            @JsonProperty("tx_hash") String txHash
        ) {
            this.blockNumber = blockNumber;
            this.iterationId = iterationId;
            this.txHash = txHash;
        }

        public Long getBlockNumber() { return blockNumber; }
        public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }

        public Long getIterationId() { return iterationId; }
        public void setIterationId(Long iterationId) { this.iterationId = iterationId; }

        public String getTxHash() { return txHash; }
        public void setTxHash(String txHash) { this.txHash = txHash; }

        @Override
        public String toString() {
            return String.format("TxExecutionId{blockNumber=%d, iterationId='%s', txHash='%s'}",
                blockNumber, iterationId, txHash);
        }
    }

    /**
     * GetTransactionRequest
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetTransactionRequest {
        @JsonProperty("block_number")
        private Long blockNumber;

        @JsonProperty("iteration_id")
        private Long iterationId;

        @JsonProperty("tx_hash")
        private String txHash;

        public GetTransactionRequest() {}

        @JsonCreator
        public GetTransactionRequest(
            @JsonProperty("block_number") Long blockNumber,
            @JsonProperty("iteration_id") Long iterationId,
            @JsonProperty("tx_hash") String txHash
        ) {
            this.blockNumber = blockNumber;
            this.iterationId = iterationId;
            this.txHash = txHash;
        }

        public Long getBlockNumber() { return blockNumber; }
        public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }

        public Long getIterationId() { return iterationId; }
        public void setIterationId(Long iterationId) { this.iterationId = iterationId; }

        public String getTxHash() { return txHash; }
        public void setTxHash(String txHash) { this.txHash = txHash; }

        @Override
        public String toString() {
            return String.format("GetTransactionRequest{blockNumber=%d, iterationId='%s', txHash='%s'}",
                blockNumber, iterationId, txHash);
        }

        public static GetTransactionRequest fromTxExecutionId(TxExecutionId txExecutionId) {
            return new GetTransactionRequest(txExecutionId.getBlockNumber(), txExecutionId.getIterationId(), txExecutionId.getTxHash());
        }

        public TxExecutionId toTxExecutionId() {
            return new TxExecutionId(blockNumber, iterationId, txHash);
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
        @JsonProperty("tx_execution_id")
        private TxExecutionId txExecutionId;

        @JsonProperty("tx_env")
        private TxEnv txEnv;

        @JsonCreator
        public TransactionExecutionPayload(
            @JsonProperty("tx_execution_id") TxExecutionId txExecutionId,
            @JsonProperty("tx_env") TxEnv txEnv
        ) {
            this.txExecutionId = txExecutionId;
            this.txEnv = txEnv;
        }

        public TxExecutionId getTxExecutionId() { return txExecutionId; }
        public void setTxExecutionId(TxExecutionId txExecutionId) { this.txExecutionId = txExecutionId; }

        public TxEnv getTxEnv() { return txEnv; }
        public void setTxEnv(TxEnv txEnv) { this.txEnv = txEnv; }
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

        private CredibleLayerMethods() {} // Utility class
    }
}
