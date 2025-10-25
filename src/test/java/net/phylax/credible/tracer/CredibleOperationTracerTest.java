package net.phylax.credible.tracer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class CredibleOperationTracerTest {
    private CredibleOperationTracer tracer;
    private WorldView worldView;
    private BlockHeader blockHeader;
    private BlockBody blockBody;
    private ProcessableBlockHeader processableBlockHeader;
    private Address miningBeneficiary;

    // NOTE: keep it max 10 because of Hash calculation
    private static final int ITERATION_COUNT = 10;

    @BeforeEach
    void setUp() {
        tracer = new CredibleOperationTracer();
        
        worldView = mock(WorldView.class);
        blockHeader = mock(BlockHeader.class);
        blockBody = mock(BlockBody.class);
        processableBlockHeader = mock(ProcessableBlockHeader.class);
        miningBeneficiary = mock(Address.class);
        when(blockHeader.getBlockHash()).thenReturn(Hash.fromHexString("0x3bdf350b4f807607bbb0ec1171590c13855964a321a1c6ebab384ade1cac6e0b"));
    }

    @Test
    void testSingleThreadedIterationIncrement() {
        final long initialIterationId = tracer.getCurrentIterationId();
        
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            tracer.traceStartBlock(worldView, processableBlockHeader, miningBeneficiary);
            // Test during processing
            assertEquals(initialIterationId + i + 1, tracer.getCurrentIterationId(), 
            "Iteration ID should increment during processing");
            tracer.traceEndBlock(blockHeader, blockBody);
        }
        
        assertEquals(initialIterationId + ITERATION_COUNT, tracer.getCurrentIterationId(),
            "Final iteration ID should match total iterations");
    }

    @Test
    void testBlockHashToIterationMapping() {
        final String[] blockHashes = new String[ITERATION_COUNT];
        
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            Hash uniqueHash = Hash.fromHexString(("0x3bdf350b4f807607bbb0ec1171590c13855964a321a1c6ebab384ade1cac6e0" + i));
            when(blockHeader.getBlockHash()).thenReturn(uniqueHash);
            blockHashes[i] = uniqueHash.toHexString();
            
            tracer.traceStartBlock(worldView, processableBlockHeader, miningBeneficiary);
            tracer.traceEndBlock(blockHeader, blockBody);
        }
        
        // Verify all mappings are correct
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            Long iterationId = tracer.getIterationForHash(blockHashes[i]);
            assertNotNull(iterationId, "Should find iteration for hash: " + blockHashes[i]);
            assertEquals(i + 1, iterationId.longValue(), 
            "Iteration ID should match processing order");
        }
    }

    @Test
    void testProcessableBlockHeaderIncrement() {
        final long initialIterationId = tracer.getCurrentIterationId();
        
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            tracer.traceStartBlock(worldView, processableBlockHeader, miningBeneficiary);
            // Test during processing
            assertEquals(initialIterationId + i + 1, tracer.getCurrentIterationId());
            tracer.traceEndBlock(blockHeader, blockBody);
        }
        
        assertEquals(initialIterationId + ITERATION_COUNT, tracer.getCurrentIterationId());
    }

    @Test
    void testClearResetsState() {
        for (int i = 0; i < 5; ++i) {
            Hash uniqueHash = Hash.fromHexString(("0x3bdf350b4f807607bbb0ec1171590c13855964a321a1c6ebab384ade1cac6e0" + i));
            when(blockHeader.getBlockHash()).thenReturn(uniqueHash);
            
            tracer.traceStartBlock(worldView, processableBlockHeader, miningBeneficiary);
            tracer.traceEndBlock(blockHeader, blockBody);
        }
        
        final long iterationBeforeClear = tracer.getCurrentIterationId();
        final String testHash = blockHeader.getBlockHash().toHexString();
        
        tracer.clear();
        
        assertEquals(iterationBeforeClear, tracer.getCurrentIterationId(), 
            "Iteration ID should persist after clear");
        assertNull(tracer.getIterationForHash(testHash), 
            "Block hash mappings should be cleared");
    }

    @Test
    void testGetIterationForHashReturnsNullForUnknownHash() {
        Long result = tracer.getIterationForHash("unknown_hash");
        assertNull(result, "Should return null for unknown block hash");
    }

    @Test
    @Timeout(5)
    void testSequentialIterationCreationWithConcurrentProcessing() throws InterruptedException {
        final int PROCESSING_THREADS = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(PROCESSING_THREADS);
        final CountDownLatch[] processingLatches = new CountDownLatch[ITERATION_COUNT];
        
        for (int i = 0; i < ITERATION_COUNT; i++) {
            processingLatches[i] = new CountDownLatch(PROCESSING_THREADS);
        }

        // Simulate sequential iteration creation
        for (int b = 0; b < ITERATION_COUNT; b++) {
            final int currentBlock = b;
            Hash uniqueHash = Hash.fromHexString(("0x3bdf350b4f807607bbb0ec1171590c13855964a321a1c6ebab384ade1cac6e0" + b));
            when(blockHeader.getBlockHash()).thenReturn(uniqueHash);
            
            // START BLOCK
            tracer.traceStartBlock(worldView, blockHeader, miningBeneficiary);
            final long iterationAfterStart = tracer.getCurrentIterationId();
            
            // CONCURRENT PROCESSING - multiple threads
            for (int i = 0; i < PROCESSING_THREADS; i++) {
                executor.submit(() -> {
                    assertEquals(iterationAfterStart, tracer.getCurrentIterationId());
                    processingLatches[currentBlock].countDown();
                });
            }
            
            processingLatches[currentBlock].await();
            
            // END BLOCK
            tracer.traceEndBlock(blockHeader, blockBody);
            
            assertEquals(iterationAfterStart, 
                tracer.getIterationForHash(uniqueHash.toHexString()).longValue());
        }
        
        executor.shutdown();
        
        // Final verification
        assertEquals(ITERATION_COUNT, tracer.getCurrentIterationId());
    }
}
