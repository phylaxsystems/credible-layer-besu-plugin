package net.phylax.credible.tracer;

import java.util.HashMap;
import java.util.Map;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

public class CredibleOperationTracer implements BlockAwareOperationTracer {
    private long currentIterationId = 0;
    private Map<Hash, Long> blockHashToIterationId = new HashMap<>();

    @Override
    public void traceStartBlock(
        final WorldView worldView,
        final BlockHeader blockHeader,
        final BlockBody blockBody,
        final Address miningBeneficiary) {
        currentIterationId++;
    }

    @Override
    public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
        blockHashToIterationId.putIfAbsent(blockHeader.getBlockHash(), currentIterationId);
    }

    @Override
    public void traceStartBlock(
        final WorldView worldView,
        final ProcessableBlockHeader processableBlockHeader,
        final Address miningBeneficiary) {
        currentIterationId++;
    }

    public void clear() {
        blockHashToIterationId.clear();
    }

    public long getCurrentIterationId() {
        return currentIterationId;
    }
}
