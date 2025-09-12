package net.phylax.credible.types;

import org.hyperledger.besu.datatypes.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.phylax.credible.types.SidecarApiModels.*;

public class TransactionConverter {
    /**
     * Convert Besu Transaction to TxEnv
     */
    public static TxEnv convertToTxEnv(Transaction transaction) {
        SidecarApiModels.TxEnv txEnv = new SidecarApiModels.TxEnv();
        
        // Caller (sender address)
        txEnv.setCaller(transaction.getSender().toHexString());
        
        // Gas limit
        txEnv.setGasLimit(transaction.getGasLimit());
        
        // Gas price handling based on transaction type
        if (transaction.getType() == TransactionType.EIP1559 || 
            transaction.getType() == TransactionType.BLOB) {
            // EIP-1559: Use maxFeePerGas as gasPrice
            transaction.getMaxFeePerGas().ifPresent(maxFee -> 
                txEnv.setGasPrice(maxFee.getAsBigInteger().toString()));
        } else {
            // Legacy: Use gasPrice
            transaction.getGasPrice().ifPresent(gasPrice ->
                txEnv.setGasPrice(gasPrice.getAsBigInteger().toString()));
        }
        
        // Transaction destination
        if (transaction.getTo().isPresent()) {
            // Contract call
            txEnv.setTransactTo(transaction.getTo().get().toHexString());
        } else {
            // Contract creation - transactTo should be null
            txEnv.setTransactTo(null);
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
            txEnv.setAccessList(new ArrayList<>()); // Empty access list
        }
        
        return txEnv;
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
                fallbackTxEnv.setGasPrice("0x" + transaction.getGasPrice().get().getAsBigInteger().toString(16));
            } else {
                fallbackTxEnv.setGasPrice("0x0");
            }
            
            // Handle destination and data safely
            if (transaction.getTo().isPresent()) {
                fallbackTxEnv.setTransactTo(transaction.getTo().get().toHexString());
            } else {
                fallbackTxEnv.setTransactTo(null);
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