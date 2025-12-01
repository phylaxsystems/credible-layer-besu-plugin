package net.phylax.credible.tracer;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.strategy.ISidecarStrategy;
import net.phylax.credible.types.SidecarApiModels.BlobExcessGasAndPrice;
import net.phylax.credible.types.SidecarApiModels.BlockEnv;
import net.phylax.credible.types.SidecarApiModels.NewIteration;

/**
 * Implementation of the operation tracer that tracks the repetitions of block creation processes
 * for the same block height.
 */
@Slf4j
public class CredibleOperationTracer implements BlockAwareOperationTracer {
    
    private final AtomicLong currentIterationId;
    private final ISidecarStrategy strategy;

    public CredibleOperationTracer(Long iterationId, ISidecarStrategy strategy) {
        this.currentIterationId = new AtomicLong(iterationId);
        this.strategy = strategy;
    }

    @Override
    public void traceStartBlock(
        final WorldView worldView,
        final ProcessableBlockHeader processableBlockHeader,
        final Address miningBeneficiary) {
        log.debug("traceStartBlock - number: {}, iteration: {}", processableBlockHeader.getNumber(), currentIterationId.get());

        // Convert fields directly to byte[] without hex string intermediate
        BlockEnv blockEnv = new BlockEnv(
            processableBlockHeader.getNumber(),
            processableBlockHeader.getCoinbase().toArrayUnsafe(),  // 20 bytes
            processableBlockHeader.getTimestamp(),
            processableBlockHeader.getGasLimit(),
            processableBlockHeader.getBaseFee().map(quantity -> quantity.getAsBigInteger().longValue()).orElse(1L), // 1 Gwei
            bigIntegerToBytes32(processableBlockHeader.getDifficulty().getAsBigInteger()),  // 32 bytes
            processableBlockHeader.getPrevRandao().map(bytes32 -> bytes32.toArrayUnsafe()).orElse(null),  // 32 bytes
            new BlobExcessGasAndPrice(0L, 1L)
        );
        NewIteration iteration = new NewIteration(currentIterationId.get(), blockEnv);

        // traceStartBlock marks a new iteration of the block production process
        try {
            strategy.newIteration(iteration);
        } catch(Exception e) {
            log.error("Error sending new iteration, block number: {}, iteration: {}, reason: {}", processableBlockHeader.getNumber(), currentIterationId.get(), e);
        }
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

    @Override
    public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
        log.debug("traceEndBlock - hash: {}, number: {}, iteration: {}, tx_count: {}",
            blockHeader.getBlockHash().toHexString(),
            blockHeader.getNumber(),
            currentIterationId.get(),
            blockBody.getTransactions().size());
        this.strategy.endIteration(blockHeader.getBlockHash().toHexString(), this.currentIterationId.get());
    }

    public long getCurrentIterationId() {
        return currentIterationId.get();
    }
}
