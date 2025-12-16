package net.phylax.credible.types;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.Optional;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;

/**
 * Tests for TransactionConverter, particularly the BigInteger to byte[] conversion
 * to ensure no precision is lost for large U256 values.
 */
public class TransactionConverterTest {

    /**
     * Convert 32-byte array (big-endian) back to BigInteger for verification.
     */
    private static BigInteger bytes32ToBigInteger(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return BigInteger.ZERO;
        }
        return new BigInteger(1, bytes);
    }

    private Transaction createMockTransaction(
            BigInteger gasPrice,
            BigInteger maxFeePerGas,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerBlobGas,
            TransactionType txType) {

        Transaction mockTransaction = mock(Transaction.class);

        // Basic transaction fields
        Hash mockTxHash = mock(Hash.class);
        when(mockTxHash.toHexString()).thenReturn("0xaabbccdd");
        when(mockTxHash.toArrayUnsafe()).thenReturn(new byte[32]);
        when(mockTransaction.getHash()).thenReturn(mockTxHash);

        when(mockTransaction.getNonce()).thenReturn(0L);
        when(mockTransaction.getGasLimit()).thenReturn(21000L);
        when(mockTransaction.getType()).thenReturn(txType);
        when(mockTransaction.getSender()).thenReturn(Address.fromHexString("0x1234567890abcdef1234567890abcdef12345678"));
        when(mockTransaction.getPayload()).thenReturn(org.apache.tuweni.bytes.Bytes.EMPTY);
        when(mockTransaction.getValue()).thenReturn(Wei.of(0));
        lenient().when(mockTransaction.getTo()).thenReturn((Optional) Optional.of(Address.fromHexString("0xabcdef1234567890abcdef1234567890abcdef12")));
        when(mockTransaction.getData()).thenReturn(Optional.empty());
        when(mockTransaction.getChainId()).thenReturn(Optional.of(BigInteger.ONE));
        when(mockTransaction.getAccessList()).thenReturn(Optional.empty());
        when(mockTransaction.getVersionedHashes()).thenReturn(Optional.empty());
        when(mockTransaction.getCodeDelegationList()).thenReturn(Optional.empty());

        // Gas price fields - use raw Optional cast to work around generic type issues
        if (gasPrice != null) {
            lenient().when(mockTransaction.getGasPrice()).thenReturn((Optional) Optional.of(Wei.of(gasPrice)));
        } else {
            lenient().when(mockTransaction.getGasPrice()).thenReturn(Optional.empty());
        }

        if (maxFeePerGas != null) {
            lenient().when(mockTransaction.getMaxFeePerGas()).thenReturn((Optional) Optional.of(Wei.of(maxFeePerGas)));
        } else {
            lenient().when(mockTransaction.getMaxFeePerGas()).thenReturn(Optional.empty());
        }

        if (maxPriorityFeePerGas != null) {
            lenient().when(mockTransaction.getMaxPriorityFeePerGas()).thenReturn((Optional) Optional.of(Wei.of(maxPriorityFeePerGas)));
        } else {
            lenient().when(mockTransaction.getMaxPriorityFeePerGas()).thenReturn(Optional.empty());
        }

        if (maxFeePerBlobGas != null) {
            lenient().when(mockTransaction.getMaxFeePerBlobGas()).thenReturn((Optional) Optional.of(Wei.of(maxFeePerBlobGas)));
        } else {
            lenient().when(mockTransaction.getMaxFeePerBlobGas()).thenReturn(Optional.empty());
        }

        return mockTransaction;
    }

    @Test
    public void testLegacyTransactionWithSmallGasPrice() {
        BigInteger gasPrice = BigInteger.valueOf(1_000_000_000L); // 1 gwei

        Transaction tx = createMockTransaction(gasPrice, null, null, null, TransactionType.FRONTIER);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

        BigInteger decoded = bytes32ToBigInteger(txEnv.getGasPrice());
        assertEquals(gasPrice, decoded);
    }

    @Test
    public void testLegacyTransactionWithLongMaxGasPrice() {
        BigInteger gasPrice = BigInteger.valueOf(Long.MAX_VALUE);

        Transaction tx = createMockTransaction(gasPrice, null, null, null, TransactionType.FRONTIER);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

        BigInteger decoded = bytes32ToBigInteger(txEnv.getGasPrice());
        assertEquals(gasPrice, decoded);
    }

    @Test
    public void testLegacyTransactionWithGasPriceExceedingLongMax() {
        // Value that exceeds Long.MAX_VALUE - this would have been truncated with longValue()
        BigInteger gasPrice = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        Transaction tx = createMockTransaction(gasPrice, null, null, null, TransactionType.FRONTIER);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

        BigInteger decoded = bytes32ToBigInteger(txEnv.getGasPrice());
        assertEquals(gasPrice, decoded);

        // Verify this value would have been corrupted by longValue()
        long truncated = gasPrice.longValue();
        assertTrue(truncated < 0, "longValue() should wrap around to negative for Long.MAX_VALUE + 1");
        assertNotEquals(gasPrice, BigInteger.valueOf(truncated));
    }

    @Test
    public void testLegacyTransactionWithVeryLargeGasPrice() {
        // Simulate a very large gas price: 1000 ETH in wei = 10^21
        // This exceeds Long.MAX_VALUE (~9.2 * 10^18)
        BigInteger gasPrice = new BigInteger("1000000000000000000000"); // 10^21

        Transaction tx = createMockTransaction(gasPrice, null, null, null, TransactionType.FRONTIER);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

        BigInteger decoded = bytes32ToBigInteger(txEnv.getGasPrice());
        assertEquals(gasPrice, decoded);

        // Verify this would overflow a long
        assertTrue(gasPrice.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }

    @Test
    public void testEip1559TransactionWithLargeMaxFeePerGas() {
        BigInteger maxFeePerGas = new BigInteger("1000000000000000000000"); // 10^21
        BigInteger maxPriorityFeePerGas = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);

        Transaction tx = createMockTransaction(null, maxFeePerGas, maxPriorityFeePerGas, null, TransactionType.EIP1559);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

        // For EIP-1559, gasPrice is set from maxFeePerGas
        BigInteger decodedGasPrice = bytes32ToBigInteger(txEnv.getGasPrice());
        assertEquals(maxFeePerGas, decodedGasPrice);

        BigInteger decodedPriorityFee = bytes32ToBigInteger(txEnv.getGasPriorityFee());
        assertEquals(maxPriorityFeePerGas, decodedPriorityFee);
    }

    @Test
    public void testBlobTransactionWithLargeMaxFeePerBlobGas() {
        BigInteger maxFeePerGas = BigInteger.valueOf(100_000_000_000L); // 100 gwei
        BigInteger maxPriorityFeePerGas = BigInteger.valueOf(2_000_000_000L); // 2 gwei
        BigInteger maxFeePerBlobGas = new BigInteger("500000000000000000000"); // Large blob gas fee

        Transaction tx = createMockTransaction(null, maxFeePerGas, maxPriorityFeePerGas, maxFeePerBlobGas, TransactionType.BLOB);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

        BigInteger decodedMaxFeePerBlobGas = bytes32ToBigInteger(txEnv.getMaxFeePerBlobGas());
        assertEquals(maxFeePerBlobGas, decodedMaxFeePerBlobGas);
    }

    @Test
    public void testMaxU256GasPrice() {
        // Maximum U256 value: 2^256 - 1
        BigInteger maxU256 = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);

        Transaction tx = createMockTransaction(maxU256, null, null, null, TransactionType.FRONTIER);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

        BigInteger decoded = bytes32ToBigInteger(txEnv.getGasPrice());
        assertEquals(maxU256, decoded);

        // Verify all bytes are 0xFF
        byte[] gasPrice = txEnv.getGasPrice();
        assertEquals(32, gasPrice.length);
        for (byte b : gasPrice) {
            assertEquals((byte) 0xFF, b);
        }
    }

    @Test
    public void testGasPriceByteArrayLength() {
        // Even small values should produce 32-byte arrays
        BigInteger smallValue = BigInteger.valueOf(255); // 0xFF - fits in 1 byte

        Transaction tx = createMockTransaction(smallValue, null, null, null, TransactionType.FRONTIER);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

        byte[] gasPrice = txEnv.getGasPrice();
        assertEquals(32, gasPrice.length);

        // First 31 bytes should be zero (left-padded)
        for (int i = 0; i < 31; i++) {
            assertEquals(0, gasPrice[i], "Byte at index " + i + " should be 0");
        }
        // Last byte should be 0xFF
        assertEquals((byte) 0xFF, gasPrice[31]);
    }

    @Test
    public void testSafeConvertWithLargeGasPrice() {
        BigInteger gasPrice = new BigInteger("1000000000000000000000"); // 10^21

        Transaction tx = createMockTransaction(gasPrice, null, null, null, TransactionType.FRONTIER);
        SidecarApiModels.TxEnv txEnv = TransactionConverter.safeConvertToTxEnv(tx);

        BigInteger decoded = bytes32ToBigInteger(txEnv.getGasPrice());
        assertEquals(gasPrice, decoded);
    }

    @Test
    public void testMultipleLargeValuesRoundTrip() {
        BigInteger[] testValues = {
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.valueOf(21000), // Typical gas limit
            BigInteger.valueOf(1_000_000_000L), // 1 gwei
            BigInteger.valueOf(100_000_000_000L), // 100 gwei
            BigInteger.valueOf(Long.MAX_VALUE),
            BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO),
            BigInteger.TWO.pow(64), // Just beyond long range
            BigInteger.TWO.pow(128), // Large value
            new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935"), // max U256
        };

        for (BigInteger original : testValues) {
            Transaction tx = createMockTransaction(original, null, null, null, TransactionType.FRONTIER);
            SidecarApiModels.TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);

            BigInteger decoded = bytes32ToBigInteger(txEnv.getGasPrice());
            assertEquals(original, decoded, "Round-trip failed for value: " + original);
        }
    }
}
