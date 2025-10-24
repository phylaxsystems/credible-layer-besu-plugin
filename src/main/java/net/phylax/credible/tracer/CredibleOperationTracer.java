package net.phylax.credible.tracer;

import java.util.HashMap;
import java.util.Map;

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
    private long currentIterationId = 0;
    // Maps blockHash -> iterationId
    private Map<String, Long> blockHashToIterationId = new HashMap<>();

    @Override
    public void traceStartBlock(
        final WorldView worldView,
        final BlockHeader blockHeader,
        final BlockBody blockBody,
        final Address miningBeneficiary) {
        handleStartBlock();
    }

    @Override
    public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
        blockHashToIterationId.putIfAbsent(blockHeader.getBlockHash().toHexString(), currentIterationId);
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
        return currentIterationId;
    }

    public Long getIterationForHash(final String blockHash) {
        return blockHashToIterationId.get(blockHash);
    }

    private void handleStartBlock() {
        currentIterationId++;
    }
}
