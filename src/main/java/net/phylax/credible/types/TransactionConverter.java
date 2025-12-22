package net.phylax.credible.types;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.hyperledger.besu.datatypes.CodeDelegation;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;

import net.phylax.credible.types.SidecarApiModels.TxEnv;

public class TransactionConverter {
    private static final List<SidecarApiModels.AccessListEntry> EMPTY_LIST = new ArrayList<>();
    private static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * Convert Besu Transaction to TxEnv.
     * Binary fields are passed directly as byte[] to avoid unnecessary hex string conversions.
     */
    public static TxEnv convertToTxEnv(Transaction transaction) throws IllegalArgumentException {
        SidecarApiModels.TxEnv txEnv = new SidecarApiModels.TxEnv();

        txEnv.setTxType(convertType(transaction.getType()));

        // Caller (sender address) - 20 bytes
        txEnv.setCaller(transaction.getSender().toArrayUnsafe());

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

        // Blob hashes - 32 bytes each
        transaction.getVersionedHashes().ifPresent(versionedHashes ->
            txEnv.setBlobHashes(versionedHashes.stream()
                .map(VersionedHash::toBytes)
                .map(bytes -> bytes.toArrayUnsafe())
                .collect(Collectors.toList()))
            );

        transaction.getCodeDelegationList().ifPresent(codeDelegationList -> {
            txEnv.setAuthorizationList(codeDelegationList.stream()
                .map(TransactionConverter::convertAuthorizationListEntry)
                .collect(Collectors.toList()));
        });

        // Transaction destination - 20 bytes or empty for contract creation
        if (transaction.getTo().isPresent()) {
            txEnv.setKind(transaction.getTo().get().toArrayUnsafe());
        } else {
            // Contract creation - empty bytes
            txEnv.setKind(EMPTY_BYTES);
        }

        // Data/payload - variable length calldata bytes
        if (transaction.getData().isPresent()) {
            txEnv.setData(transaction.getData().get().toArrayUnsafe());
        } else {
            txEnv.setData(transaction.getPayload().toArrayUnsafe());
        }

        // Value - 32 bytes U256 big-endian
        txEnv.setValue(bigIntegerToBytes32(transaction.getValue().getAsBigInteger()));

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

    /**
     * Convert BigInteger to 32-byte array (big-endian, left-padded with zeros).
     */
    private static byte[] bigIntegerToBytes32(BigInteger value) {
        byte[] bytes = new byte[32];
        if (value == null || value.equals(BigInteger.ZERO)) {
            return bytes; // All zeros
        }
        byte[] valueBytes = value.toByteArray();
        // Handle potential leading zero byte from BigInteger's two's complement representation
        int srcOffset = valueBytes[0] == 0 && valueBytes.length > 1 ? 1 : 0;
        int length = valueBytes.length - srcOffset;
        int destOffset = 32 - length;
        if (destOffset >= 0) {
            System.arraycopy(valueBytes, srcOffset, bytes, destOffset, length);
        } else {
            // Value is larger than 32 bytes (shouldn't happen for valid U256)
            System.arraycopy(valueBytes, srcOffset - destOffset, bytes, 0, 32);
        }
        return bytes;
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

    private static byte convertType(TransactionType type) throws IllegalArgumentException {
        switch(type) {
            case FRONTIER: return 0;
            case ACCESS_LIST: return 1;
            case EIP1559: return 2;
            case BLOB: return 3;
            case DELEGATE_CODE: return 4;
            default: throw new IllegalArgumentException("Unknown transaction type: " + type);
        }
    }

    private static SidecarApiModels.AuthorizationListEntry convertAuthorizationListEntry(
            CodeDelegation codeDelegation) {
        return new SidecarApiModels.AuthorizationListEntry(
            codeDelegation.address().toArrayUnsafe(),  // 20 bytes
            codeDelegation.v(),
            bigIntegerToBytes32(codeDelegation.r()),   // 32 bytes
            bigIntegerToBytes32(codeDelegation.s()),   // 32 bytes
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

        // Address - 20 bytes
        byte[] address = besuEntry.address().toArrayUnsafe();

        // Storage keys - 32 bytes each
        List<byte[]> storageKeys = besuEntry.storageKeys().stream()
            .map(key -> key.toArrayUnsafe())
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

            fallbackTxEnv.setCaller(transaction.getSender().toArrayUnsafe());
            fallbackTxEnv.setGasLimit(transaction.getGasLimit());
            fallbackTxEnv.setValue(bigIntegerToBytes32(transaction.getValue().getAsBigInteger()));
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
                fallbackTxEnv.setKind(transaction.getTo().get().toArrayUnsafe());
            } else {
                fallbackTxEnv.setKind(EMPTY_BYTES);
            }

            // Handle data safely
            try {
                if (transaction.getData().isPresent()) {
                    fallbackTxEnv.setData(transaction.getData().get().toArrayUnsafe());
                } else {
                    fallbackTxEnv.setData(transaction.getPayload().toArrayUnsafe());
                }
            } catch (Exception dataException) {
                fallbackTxEnv.setData(EMPTY_BYTES);
            }

            // Handle chain ID safely
            transaction.getChainId().ifPresentOrElse(
                chainId -> fallbackTxEnv.setChainId(chainId.longValue()),
                () -> fallbackTxEnv.setChainId(1L)
            );

            return fallbackTxEnv;
        }
    }
}