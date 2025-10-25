package net.phylax.credible.tracer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

/**
 * Implementation of the operation tracer that tracks the repetitions of block creation processes
 * for the same block height.
 */
public class CredibleOperationTracer implements BlockAwareOperationTracer {
    private final AtomicLong currentIterationId = new AtomicLong(0L);
    // Maps blockHash -> iterationId
    private final Map<String, Long> blockHashToIterationId = new ConcurrentHashMap<>();

    @Override
    public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
        blockHashToIterationId.putIfAbsent(blockHeader.getBlockHash().toHexString(), currentIterationId.get());
    }

    @Override
    public void traceStartBlock(
        final WorldView worldView,
        final ProcessableBlockHeader processableBlockHeader,
        final Address miningBeneficiary) {
        handleStartBlock();
    }

    // Clears the internal state
    public void clear() {
        blockHashToIterationId.clear();
    }

    public long getCurrentIterationId() {
        return currentIterationId.get();
    }

    public Long getIterationForHash(final String blockHash) {
        return blockHashToIterationId.get(blockHash);
    }

    private void handleStartBlock() {
        currentIterationId.incrementAndGet();
    }
}
