package net.phylax.credible.types;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.CodeDelegation;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;

import net.phylax.credible.types.SidecarApiModels.TxEnv;

public class TransactionConverter {
    private static final ThreadLocal<StringBuilder> STRING_BUILDER = 
        ThreadLocal.withInitial(() -> new StringBuilder(128));

    private static final List<SidecarApiModels.AccessListEntry> EMPTY_LIST = new ArrayList<>();

    /**
     * Convert Besu Transaction to TxEnv
     */
    public static TxEnv convertToTxEnv(Transaction transaction) {
        SidecarApiModels.TxEnv txEnv = new SidecarApiModels.TxEnv();
        
        txEnv.setTxType(convertType(transaction.getType()));

        // Caller (sender address)
        txEnv.setCaller(addressToString(transaction.getSender()));
        
        // Gas limit
        txEnv.setGasLimit(transaction.getGasLimit());
        
        // Gas price handling based on transaction type
        if (supportsEip1559(transaction.getType())) {
            // EIP-1559: Use maxFeePerGas as gasPrice
            transaction.getMaxFeePerGas().ifPresent(maxFee -> 
                txEnv.setGasPrice(maxFee.getAsBigInteger().longValue()));
            transaction.getMaxPriorityFeePerGas().ifPresent(maxPriorityFee -> 
                txEnv.setGasPriorityFee(maxPriorityFee.getAsBigInteger().longValue()));
        } else {
            // Legacy: Use gasPrice
            transaction.getGasPrice().ifPresent(gasPrice ->
                txEnv.setGasPrice(gasPrice.getAsBigInteger().longValue()));
        }

        transaction.getMaxFeePerBlobGas().ifPresent(maxFeePerBlobGas ->
            txEnv.setMaxFeePerBlobGas(maxFeePerBlobGas.getAsBigInteger().longValue()));

        transaction.getVersionedHashes().ifPresent(versionedHashes ->
            txEnv.setBlobHashes(versionedHashes.stream()
                .map(VersionedHash::toString)
                .collect(Collectors.toList()))
            );

        transaction.getCodeDelegationList().ifPresent(codeDelegationList -> {
            txEnv.setAuthorizationList(codeDelegationList.stream()
                .map(TransactionConverter::convertAuthorizationListEntry)
                .collect(Collectors.toList()));
        });
        
        // Transaction destination
        if (transaction.getTo().isPresent()) {
            // Contract call
            txEnv.setKind(addressToString(transaction.getTo().get()));
        } else {
            // Contract creation - kind should be empty/0x (taken from spec)
            txEnv.setKind("0x");
        }
        
        // Data/payload - use getData() if available, otherwise getPayload()
        if (transaction.getData().isPresent()) {
            txEnv.setData(transaction.getData().get().toHexString());
        } else {
            txEnv.setData(transaction.getPayload().toHexString());
        }
        
        // Value
        txEnv.setValue("0x" + transaction.getValue().getAsBigInteger().toString(16));
        
        // Nonce
        txEnv.setNonce(transaction.getNonce());
        
        // Chain ID
        transaction.getChainId().ifPresent(chainId ->
            txEnv.setChainId(chainId.longValue()));
        
        // Access List (EIP-2930 and later)
        if (transaction.getAccessList().isPresent()) {
            List<SidecarApiModels.AccessListEntry> accessList = convertAccessList(transaction.getAccessList().get());
            txEnv.setAccessList(accessList);
        } else {
            txEnv.setAccessList(EMPTY_LIST); // Empty access list
        }
        
        return txEnv;
    }

    private static String addressToString(Address address) {
        StringBuilder sb = STRING_BUILDER.get();
        sb.setLength(0);
        sb.append(address.toHexString());
        return sb.toString();
    }

    private static boolean supportsEip1559(TransactionType type) {
        switch(type){
            case ACCESS_LIST:
            case FRONTIER:
                return false;
            default:
                return true;
        }
    }

    private static byte convertType(TransactionType type) {
        switch(type) {
            case FRONTIER: return 0;
            case ACCESS_LIST: return 1;
            case EIP1559: return 2;
            case BLOB: return 3;
            case DELEGATE_CODE: return 4;
            // TODO: default behavior expected
            default: return -1;
        }
    }

    private static SidecarApiModels.AuthorizationListEntry convertAuthorizationListEntry(
            CodeDelegation codeDelegation) {
        return new SidecarApiModels.AuthorizationListEntry(
            codeDelegation.address().toHexString(),
            codeDelegation.v(),
            "0x" + codeDelegation.r().toString(16),
            "0x" +codeDelegation.s().toString(16),
            codeDelegation.chainId().longValue(),
            codeDelegation.nonce());
    }
    
    /**
     * Convert Besu AccessList to TxEnv AccessList
     */
    private static List<SidecarApiModels.AccessListEntry> convertAccessList(
            List<org.hyperledger.besu.datatypes.AccessListEntry> besuAccessList) {
        
        return besuAccessList.stream()
            .map(TransactionConverter::convertAccessListEntry)
            .collect(Collectors.toList());
    }
    
    /**
     * Convert individual AccessListEntry
     */
    private static SidecarApiModels.AccessListEntry convertAccessListEntry(
            org.hyperledger.besu.datatypes.AccessListEntry besuEntry) {
        
        String address = besuEntry.getAddressString();
        
        List<String> storageKeys = besuEntry.getStorageKeysString().stream()
            .collect(Collectors.toList());
        
        return new SidecarApiModels.AccessListEntry(address, storageKeys);
    }
    
    /**
     * Helper method to get transaction type name
     */
    public static String getTransactionTypeName(Transaction transaction) {
        switch (transaction.getType()) {
            case FRONTIER: return "Legacy";
            case ACCESS_LIST: return "EIP-2930";
            case EIP1559: return "EIP-1559";
            case BLOB: return "EIP-4844";
            default: return "Unknown";
        }
    }
    
    /**
     * Get transaction type as integer
     */
    public static int getTransactionTypeAsInt(Transaction transaction) {
        return transaction.getType().getEthSerializedType();
    }
    
    /**
     * Comprehensive conversion with error handling
     */
    public static TxEnv safeConvertToTxEnv(Transaction transaction) {
        try {
            return convertToTxEnv(transaction);
        } catch (Exception e) {
            // Log error and return basic TxEnv
            System.err.println("Error converting transaction: " + e.getMessage());
            e.printStackTrace();
            
            // Return minimal TxEnv with available data
            TxEnv fallbackTxEnv = new TxEnv();
            
            fallbackTxEnv.setCaller(transaction.getSender().toHexString());
            fallbackTxEnv.setGasLimit(transaction.getGasLimit());
            fallbackTxEnv.setValue("0x" + transaction.getValue().getAsBigInteger().toString(16));
            fallbackTxEnv.setNonce(transaction.getNonce());
            fallbackTxEnv.setAccessList(new ArrayList<>());
            
            // Handle gas price safely
            if (transaction.getGasPrice().isPresent()) {
                fallbackTxEnv.setGasPrice(transaction.getGasPrice().get().getAsBigInteger().longValue());
            } else {
                fallbackTxEnv.setGasPrice(0L);
            }
            
            // Handle destination and data safely
            if (transaction.getTo().isPresent()) {
                fallbackTxEnv.setKind(transaction.getTo().get().toHexString());
            } else {
                fallbackTxEnv.setKind(null);
            }
            
            // Handle data safely
            try {
                if (transaction.getData().isPresent()) {
                    fallbackTxEnv.setData(transaction.getData().get().toHexString());
                } else {
                    fallbackTxEnv.setData(transaction.getPayload().toHexString());
                }
            } catch (Exception dataException) {
                fallbackTxEnv.setData("0x");
            }
            
            // Handle chain ID safely
            transaction.getChainId().ifPresentOrElse(
                chainId -> fallbackTxEnv.setChainId(chainId.longValue()),
                () -> fallbackTxEnv.setChainId(1L)
            );
            
            return fallbackTxEnv;
        }
    }
    
    /**
     * Convert with additional metadata
     */
    public static class TxEnvWithMetadata {
        private final TxEnv txEnv;
        private final String transactionHash;
        private final String typeName;
        private final int typeCode;
        private final boolean isContractCreation;
        
        public TxEnvWithMetadata(TxEnv txEnv, String transactionHash, 
                               String typeName, int typeCode, boolean isContractCreation) {
            this.txEnv = txEnv;
            this.transactionHash = transactionHash;
            this.typeName = typeName;
            this.typeCode = typeCode;
            this.isContractCreation = isContractCreation;
        }
        
        // Getters
        public TxEnv getTxEnv() { return txEnv; }
        public String getTransactionHash() { return transactionHash; }
        public String getTypeName() { return typeName; }
        public int getTypeCode() { return typeCode; }
        public boolean isContractCreation() { return isContractCreation; }
        
        @Override
        public String toString() {
            return String.format("TxEnvWithMetadata{hash='%s', type='%s'(%d), isCreate=%b, txEnv=%s}",
                transactionHash, typeName, typeCode, isContractCreation, txEnv);
        }
    }
    
    /**
     * Convert transaction with metadata
     */
    public static TxEnvWithMetadata convertWithMetadata(Transaction transaction) {
        TxEnv txEnv = convertToTxEnv(transaction);
        String hash = transaction.getHash().toHexString();
        String typeName = getTransactionTypeName(transaction);
        int typeCode = getTransactionTypeAsInt(transaction);
        boolean isCreate = transaction.getTo().isEmpty();
        
        return new TxEnvWithMetadata(txEnv, hash, typeName, typeCode, isCreate);
    }
    
    /**
     * Safe convert with metadata
     */
    public static TxEnvWithMetadata safeConvertWithMetadata(Transaction transaction) {
        TxEnv txEnv = safeConvertToTxEnv(transaction);
        String hash = transaction.getHash().toHexString();
        String typeName = getTransactionTypeName(transaction);
        int typeCode = getTransactionTypeAsInt(transaction);
        boolean isCreate = transaction.getTo().isEmpty();
        
        return new TxEnvWithMetadata(txEnv, hash, typeName, typeCode, isCreate);
    }
}