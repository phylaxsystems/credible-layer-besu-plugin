package net.phylax.credible.aeges;

import java.math.BigInteger;

import com.google.protobuf.ByteString;
import org.hyperledger.besu.datatypes.Transaction;

import aeges.v1.Aeges;


/**
 * Converts Besu Transaction objects to Aeges protobuf Transaction messages.
 */
public final class AegesModelConverter {

    private AegesModelConverter() {}

    /**
     * Convert a Besu Transaction to an Aeges proto Transaction.
     */
    public static Aeges.Transaction toProtoTransaction(Transaction tx) {
        var builder = Aeges.Transaction.newBuilder()
            .setHash(ByteString.copyFrom(tx.getHash().toArray()))
            .setSender(ByteString.copyFrom(tx.getSender().toArray()))
            .setValue(ByteString.copyFrom(bigIntToBytes32(tx.getValue().getAsBigInteger())))
            .setNonce(tx.getNonce())
            .setType(tx.getType().ordinal())
            .setPayload(ByteString.copyFrom(tx.getPayload().toArray()))
            .setGasLimit(tx.getGasLimit());

        // Optional core fields
        tx.getTo().ifPresent(to ->
            builder.setTo(ByteString.copyFrom(to.toArray())));
        tx.getChainId().ifPresent(chainId ->
            builder.setChainId(chainId.longValueExact()));

        // Gas fields
        tx.getGasPrice().ifPresent(gp ->
            builder.setGasPrice(gp.getAsBigInteger().longValueExact()));
        tx.getMaxFeePerGas().ifPresent(mf ->
            builder.setMaxFeePerGas(mf.getAsBigInteger().longValueExact()));
        tx.getMaxPriorityFeePerGas().ifPresent(mpf ->
            builder.setMaxPriorityFeePerGas(mpf.getAsBigInteger().longValueExact()));
        tx.getMaxFeePerBlobGas().ifPresent(mbf ->
            builder.setMaxFeePerBlobGas(mbf.getAsBigInteger().longValueExact()));

        // EIP-2930 access list
        tx.getAccessList().ifPresent(al -> al.forEach(entry -> {
            var alBuilder = Aeges.AccessListEntry.newBuilder()
                .setAddress(ByteString.copyFrom(entry.address().toArray()));
            entry.storageKeys().forEach(key ->
                alBuilder.addStorageKeys(ByteString.copyFrom(key.toArray())));
            builder.addAccessList(alBuilder);
        }));

        // EIP-4844 versioned hashes
        tx.getVersionedHashes().ifPresent(hashes -> hashes.forEach(vh ->
            builder.addVersionedHashes(ByteString.copyFrom(vh.toBytes().toArray()))));

        // EIP-7702 code delegations
        tx.getCodeDelegationList().ifPresent(cds -> cds.forEach(cd -> {
            var cdBuilder = Aeges.CodeDelegation.newBuilder()
                .setChainId(cd.chainId().longValueExact())
                .setAddress(ByteString.copyFrom(cd.address().toArray()))
                .setNonce(cd.nonce())
                .setV(cd.v())
                .setR(ByteString.copyFrom(bigIntToBytes32(cd.r())))
                .setS(ByteString.copyFrom(bigIntToBytes32(cd.s())));
            builder.addCodeDelegationList(cdBuilder);
        }));

        return builder.build();
    }

    /**
     * Convert a BigInteger to a 32-byte big-endian array, zero-padded on the left.
     */
    static byte[] bigIntToBytes32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] result = new byte[32];
        if (raw.length <= 32) {
            System.arraycopy(raw, 0, result, 32 - raw.length, raw.length);
        } else {
            // BigInteger may prepend a leading zero byte for sign; strip it
            System.arraycopy(raw, raw.length - 32, result, 0, 32);
        }
        return result;
    }
}
