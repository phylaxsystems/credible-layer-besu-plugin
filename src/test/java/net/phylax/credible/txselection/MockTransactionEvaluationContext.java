package net.phylax.credible.txselection;

import static org.mockito.Mockito.*;

import java.util.Optional;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

import com.google.common.base.Stopwatch;

public class MockTransactionEvaluationContext implements TransactionEvaluationContext {
    private final PendingTransaction mockPendingTransaction;
    private final Transaction mockTransaction;
    private final ProcessableBlockHeader mockPendingBlockHeader;
    private String txHash;
    
    public MockTransactionEvaluationContext(String txHash) {
        this.mockTransaction = mock(Transaction.class);
        this.mockPendingTransaction = mock(PendingTransaction.class);
        this.mockPendingBlockHeader = mock(BlockHeader.class);
        this.txHash = txHash;
        
        setupDefaultMockBehaviors();
    }

    public MockTransactionEvaluationContext(
            PendingTransaction pendingTransaction,
            Transaction transaction,
            BlockHeader pendingBlockHeader,
            boolean cancelled) {
        this.mockTransaction = transaction;
        this.mockPendingTransaction = pendingTransaction;
        this.mockPendingBlockHeader = pendingBlockHeader;
    }

    private void setupDefaultMockBehaviors() {
        Hash mockTxHash = mock(Hash.class);
        when(mockTxHash.toHexString()).thenReturn(this.txHash);
        
        when(mockTransaction.getHash()).thenReturn(mockTxHash);
        when(mockTransaction.getNonce()).thenReturn(0L);
        when(mockTransaction.getGasLimit()).thenReturn(21000L);

        lenient().when(mockTransaction.getGasPrice()).thenReturn((Optional) Optional.of(Wei.of(1000000000L)));
        lenient().when(mockTransaction.getMaxFeePerGas()).thenReturn((Optional) Optional.of(Wei.of(2000000000L)));
        lenient().when(mockTransaction.getMaxPriorityFeePerGas()).thenReturn((Optional) Optional.of(Wei.of(1000000000L)));
        
        lenient().when(mockTransaction.getType()).thenReturn(org.hyperledger.besu.datatypes.TransactionType.FRONTIER);
        when(mockTransaction.getSender()).thenReturn(Address.fromHexString("0x1234567890abcdef1234567890abcdef12345678"));
        when(mockTransaction.getPayload()).thenReturn(Address.fromHexString("0x"));
        lenient().when(mockTransaction.getValue()).thenReturn(Wei.of(10L));

        when(mockPendingTransaction.getTransaction()).thenReturn(mockTransaction);
        when(mockPendingTransaction.isReceivedFromLocalSource()).thenReturn(false);
        when(mockPendingTransaction.getAddedAt()).thenReturn(System.currentTimeMillis());
    }
    
    
    public Object getMockPendingBlockHeader() {
        return mockPendingBlockHeader;
    }

    @Override
    public ProcessableBlockHeader getPendingBlockHeader() {
        return (ProcessableBlockHeader) mockPendingBlockHeader;
    }

    @Override
    public PendingTransaction getPendingTransaction() {
        return (PendingTransaction) mockPendingTransaction;
    }

    @Override
    public Stopwatch getEvaluationTimer() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEvaluationTimer'");
    }

    @Override
    public Wei getTransactionGasPrice() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransactionGasPrice'");
    }

    @Override
    public Wei getMinGasPrice() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMinGasPrice'");
    }

    public boolean isCancelled() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isCancelled'");
    }
}