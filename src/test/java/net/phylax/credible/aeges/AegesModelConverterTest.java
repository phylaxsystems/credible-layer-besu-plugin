package net.phylax.credible.aeges;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

import aeges.v1.Aeges;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


public class AegesModelConverterTest {

    // --- toProtoTransaction tests ---

    @Test
    public void legacyTransaction() {
        Transaction tx = mockLegacyTransaction(
            "0xaabbccdd11223344556677889900aabbccdd11223344556677889900aabbccdd",
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222",
            Wei.of(1_000_000_000L),
            42L,
            21000L,
            "0xdeadbeef"
        );

        Aeges.Transaction proto = AegesModelConverter.toProtoTransaction(tx);

        // Core fields
        assertArrayEquals(
            hexToBytes("0xaabbccdd11223344556677889900aabbccdd11223344556677889900aabbccdd"),
            proto.getHash().toByteArray());
        assertArrayEquals(
            hexToBytes("0x1111111111111111111111111111111111111111"),
            proto.getSender().toByteArray());
        assertTrue(proto.hasTo());
        assertArrayEquals(
            hexToBytes("0x2222222222222222222222222222222222222222"),
            proto.getTo().toByteArray());
        assertEquals(42L, proto.getNonce());
        assertEquals(TransactionType.FRONTIER.ordinal(), proto.getType());
        assertEquals(21000L, proto.getGasLimit());
        assertArrayEquals(hexToBytes("0xdeadbeef"), proto.getPayload().toByteArray());

        // Value: 1_000_000_000 as 32-byte big-endian
        byte[] valueBytes = proto.getValue().toByteArray();
        assertEquals(32, valueBytes.length);
        assertEquals(BigInteger.valueOf(1_000_000_000L), new BigInteger(1, valueBytes));

        // Gas price set for legacy
        assertTrue(proto.hasGasPrice());
        assertEquals(2_000_000_000L, proto.getGasPrice());

        // EIP-1559 fields not set
        assertFalse(proto.hasMaxFeePerGas());
        assertFalse(proto.hasMaxPriorityFeePerGas());
        assertFalse(proto.hasMaxFeePerBlobGas());

        // No access list, versioned hashes, or code delegations
        assertEquals(0, proto.getAccessListCount());
        assertEquals(0, proto.getVersionedHashesCount());
        assertEquals(0, proto.getCodeDelegationListCount());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void eip1559Transaction() {
        Transaction tx = mock(Transaction.class);
        setupBaseMock(tx,
            "0x1122334455667788990011223344556677889900112233445566778899001122",
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            TransactionType.EIP1559);
        when(tx.getTo()).thenReturn((Optional) Optional.of(Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
        when(tx.getValue()).thenReturn(Wei.ZERO);
        when(tx.getNonce()).thenReturn(10L);
        when(tx.getGasLimit()).thenReturn(50000L);
        when(tx.getPayload()).thenReturn(Bytes.EMPTY);

        // EIP-1559 fields
        lenient().when(tx.getGasPrice()).thenReturn(Optional.empty());
        when(tx.getMaxFeePerGas()).thenReturn((Optional) Optional.of(Wei.of(3_000_000_000L)));
        when(tx.getMaxPriorityFeePerGas()).thenReturn((Optional) Optional.of(Wei.of(1_500_000_000L)));
        lenient().when(tx.getMaxFeePerBlobGas()).thenReturn(Optional.empty());
        lenient().when(tx.getChainId()).thenReturn(Optional.of(BigInteger.ONE));
        lenient().when(tx.getAccessList()).thenReturn(Optional.empty());
        lenient().when(tx.getVersionedHashes()).thenReturn(Optional.empty());
        lenient().when(tx.getCodeDelegationList()).thenReturn(Optional.empty());

        Aeges.Transaction proto = AegesModelConverter.toProtoTransaction(tx);

        assertEquals(TransactionType.EIP1559.ordinal(), proto.getType());
        assertFalse(proto.hasGasPrice());
        assertTrue(proto.hasMaxFeePerGas());
        assertEquals(3_000_000_000L, proto.getMaxFeePerGas());
        assertTrue(proto.hasMaxPriorityFeePerGas());
        assertEquals(1_500_000_000L, proto.getMaxPriorityFeePerGas());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void contractCreationTransaction() {
        Transaction tx = mock(Transaction.class);
        setupBaseMock(tx,
            "0xff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00",
            "0xcccccccccccccccccccccccccccccccccccccccc",
            TransactionType.FRONTIER);
        when(tx.getTo()).thenReturn(Optional.empty()); // Contract creation
        when(tx.getValue()).thenReturn(Wei.ZERO);
        when(tx.getNonce()).thenReturn(0L);
        when(tx.getGasLimit()).thenReturn(500000L);
        when(tx.getPayload()).thenReturn(Bytes.fromHexString("0x6080604052"));
        lenient().when(tx.getGasPrice()).thenReturn((Optional) Optional.of(Wei.of(1_000_000_000L)));
        lenient().when(tx.getMaxFeePerGas()).thenReturn(Optional.empty());
        lenient().when(tx.getMaxPriorityFeePerGas()).thenReturn(Optional.empty());
        lenient().when(tx.getMaxFeePerBlobGas()).thenReturn(Optional.empty());
        lenient().when(tx.getChainId()).thenReturn(Optional.empty());
        lenient().when(tx.getAccessList()).thenReturn(Optional.empty());
        lenient().when(tx.getVersionedHashes()).thenReturn(Optional.empty());
        lenient().when(tx.getCodeDelegationList()).thenReturn(Optional.empty());

        Aeges.Transaction proto = AegesModelConverter.toProtoTransaction(tx);

        assertFalse(proto.hasTo(), "Contract creation should not have 'to' field set");
        assertArrayEquals(hexToBytes("0x6080604052"), proto.getPayload().toByteArray());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void transactionWithAccessList() {
        Transaction tx = mock(Transaction.class);
        setupBaseMock(tx,
            "0x0000000000000000000000000000000000000000000000000000000000000001",
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            TransactionType.ACCESS_LIST);
        when(tx.getTo()).thenReturn((Optional) Optional.of(Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
        when(tx.getValue()).thenReturn(Wei.ZERO);
        when(tx.getNonce()).thenReturn(0L);
        when(tx.getGasLimit()).thenReturn(21000L);
        when(tx.getPayload()).thenReturn(Bytes.EMPTY);
        lenient().when(tx.getGasPrice()).thenReturn((Optional) Optional.of(Wei.of(1_000_000_000L)));
        lenient().when(tx.getMaxFeePerGas()).thenReturn(Optional.empty());
        lenient().when(tx.getMaxPriorityFeePerGas()).thenReturn(Optional.empty());
        lenient().when(tx.getMaxFeePerBlobGas()).thenReturn(Optional.empty());
        lenient().when(tx.getChainId()).thenReturn(Optional.of(BigInteger.ONE));
        lenient().when(tx.getVersionedHashes()).thenReturn(Optional.empty());
        lenient().when(tx.getCodeDelegationList()).thenReturn(Optional.empty());

        // Access list with 2 entries
        Address addr1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
        Bytes32 key1 = Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
        Bytes32 key2 = Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000002");
        AccessListEntry entry1 = new AccessListEntry(addr1, List.of(key1, key2));

        Address addr2 = Address.fromHexString("0x2222222222222222222222222222222222222222");
        AccessListEntry entry2 = new AccessListEntry(addr2, List.of());

        when(tx.getAccessList()).thenReturn(Optional.of(List.of(entry1, entry2)));

        Aeges.Transaction proto = AegesModelConverter.toProtoTransaction(tx);

        assertEquals(2, proto.getAccessListCount());

        // First entry: address + 2 storage keys
        Aeges.AccessListEntry protoEntry1 = proto.getAccessList(0);
        assertArrayEquals(addr1.toArray(), protoEntry1.getAddress().toByteArray());
        assertEquals(2, protoEntry1.getStorageKeysCount());
        assertArrayEquals(key1.toArray(), protoEntry1.getStorageKeys(0).toByteArray());
        assertArrayEquals(key2.toArray(), protoEntry1.getStorageKeys(1).toByteArray());

        // Second entry: address + no storage keys
        Aeges.AccessListEntry protoEntry2 = proto.getAccessList(1);
        assertArrayEquals(addr2.toArray(), protoEntry2.getAddress().toByteArray());
        assertEquals(0, protoEntry2.getStorageKeysCount());
    }

    // --- bigIntToBytes32 tests ---

    @Test
    public void bigIntToBytes32_zeroPadding() {
        byte[] result = AegesModelConverter.bigIntToBytes32(BigInteger.ONE);
        assertEquals(32, result.length);
        // All zeros except last byte
        for (int i = 0; i < 31; i++) {
            assertEquals(0, result[i], "byte " + i + " should be 0");
        }
        assertEquals(1, result[31]);
    }

    @Test
    public void bigIntToBytes32_zero() {
        byte[] result = AegesModelConverter.bigIntToBytes32(BigInteger.ZERO);
        assertEquals(32, result.length);
        for (int i = 0; i < 32; i++) {
            assertEquals(0, result[i]);
        }
    }

    @Test
    public void bigIntToBytes32_fullWidth() {
        // max uint256: 2^256 - 1
        BigInteger maxUint256 = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);
        byte[] result = AegesModelConverter.bigIntToBytes32(maxUint256);
        assertEquals(32, result.length);
        for (int i = 0; i < 32; i++) {
            assertEquals((byte) 0xFF, result[i], "byte " + i + " should be 0xFF");
        }
    }

    @Test
    public void bigIntToBytes32_leadingZeroByte() {
        // 128 = 0x80, BigInteger.toByteArray() returns [0x00, 0x80] (sign bit)
        BigInteger val = BigInteger.valueOf(128);
        byte[] raw = val.toByteArray();
        assertEquals(2, raw.length, "BigInteger should produce 2-byte array for 128");

        byte[] result = AegesModelConverter.bigIntToBytes32(val);
        assertEquals(32, result.length);
        assertEquals(0, result[30]);
        assertEquals((byte) 0x80, result[31]);
    }

    @Test
    public void bigIntToBytes32_roundTrip() {
        BigInteger original = new BigInteger("123456789012345678901234567890");
        byte[] bytes32 = AegesModelConverter.bigIntToBytes32(original);
        BigInteger restored = new BigInteger(1, bytes32);
        assertEquals(original, restored);
    }

    // --- Helpers ---

    private void setupBaseMock(Transaction tx, String hashHex, String senderHex, TransactionType type) {
        Hash hash = Hash.fromHexString(hashHex);
        when(tx.getHash()).thenReturn(hash);
        when(tx.getSender()).thenReturn(Address.fromHexString(senderHex));
        when(tx.getType()).thenReturn(type);
    }

    @SuppressWarnings("unchecked")
    private Transaction mockLegacyTransaction(
            String hashHex, String senderHex, String toHex,
            Wei value, long nonce, long gasLimit, String payloadHex) {
        Transaction tx = mock(Transaction.class);
        setupBaseMock(tx, hashHex, senderHex, TransactionType.FRONTIER);
        when(tx.getTo()).thenReturn((Optional) Optional.of(Address.fromHexString(toHex)));
        when(tx.getValue()).thenReturn(value);
        when(tx.getNonce()).thenReturn(nonce);
        when(tx.getGasLimit()).thenReturn(gasLimit);
        when(tx.getPayload()).thenReturn(Bytes.fromHexString(payloadHex));
        lenient().when(tx.getGasPrice()).thenReturn((Optional) Optional.of(Wei.of(2_000_000_000L)));
        lenient().when(tx.getMaxFeePerGas()).thenReturn(Optional.empty());
        lenient().when(tx.getMaxPriorityFeePerGas()).thenReturn(Optional.empty());
        lenient().when(tx.getMaxFeePerBlobGas()).thenReturn(Optional.empty());
        lenient().when(tx.getChainId()).thenReturn(Optional.empty());
        lenient().when(tx.getAccessList()).thenReturn(Optional.empty());
        lenient().when(tx.getVersionedHashes()).thenReturn(Optional.empty());
        lenient().when(tx.getCodeDelegationList()).thenReturn(Optional.empty());
        return tx;
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        String cleanHex = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (cleanHex.length() % 2 != 0) cleanHex = "0" + cleanHex;
        byte[] bytes = new byte[cleanHex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(cleanHex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
