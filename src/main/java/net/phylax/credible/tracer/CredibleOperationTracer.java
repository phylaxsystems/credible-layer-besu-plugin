package net.phylax.credible.tracer;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;
import org.slf4j.Logger;

import net.phylax.credible.CredibleLayerPlugin;
import net.phylax.credible.strategy.ISidecarStrategy;
import net.phylax.credible.types.SidecarApiModels.BlobExcessGasAndPrice;
import net.phylax.credible.types.SidecarApiModels.BlockEnv;
import net.phylax.credible.types.SidecarApiModels.NewIteration;
import net.phylax.credible.utils.CredibleLogger;

/**
 * Implementation of the operation tracer that tracks the repetitions of block creation processes
 * for the same block height.
 */
public class CredibleOperationTracer implements BlockAwareOperationTracer {
    private static final Logger LOG = CredibleLogger.getLogger(CredibleLayerPlugin.class);

    private final AtomicLong currentIterationId;
    private final ISidecarStrategy strategy;

    public CredibleOperationTracer(Long iterationId, ISidecarStrategy strategy) {
        this.currentIterationId = new AtomicLong(iterationId);
        this.strategy = strategy;
    }

    @Override
    public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
        LOG.debug("traceEndBlock - Hash: {}, Number: {}, Iteration: {}", blockHeader.getBlockHash().toHexString(), blockHeader.getNumber(), currentIterationId.get());
        this.strategy.endIteration(blockHeader.getBlockHash().toHexString(), this.currentIterationId.get());
    }

    @Override
    public void traceStartBlock(
        final WorldView worldView,
        final ProcessableBlockHeader processableBlockHeader,
        final Address miningBeneficiary) {
        LOG.debug("traceStartBlock - Number: {}, Iteration: {}", processableBlockHeader.getNumber(), currentIterationId.get());
        BlockEnv blockEnv = new BlockEnv(
            processableBlockHeader.getNumber(),
            processableBlockHeader.getCoinbase().toHexString(),
            processableBlockHeader.getTimestamp(),
            processableBlockHeader.getGasLimit(),
            processableBlockHeader.getBaseFee().map(quantity -> quantity.getAsBigInteger().longValue()).orElse(1L), // 1 Gwei
            processableBlockHeader.getDifficulty().toString(),
            processableBlockHeader.getPrevRandao().map(Bytes32::toHexString).orElse(null),
            new BlobExcessGasAndPrice(0L, 1L)
        );
        NewIteration iteration = new NewIteration(currentIterationId.get(), blockEnv);

        try {
            strategy.newIteration(iteration).join();
        } catch(Exception e) {
            LOG.error("Error sending new iteration, block number: {}, iteration: {}, reason: {}", processableBlockHeader.getNumber(), currentIterationId.get(), e);
        }
    }

    public long getCurrentIterationId() {
        return currentIterationId.get();
    }
}
