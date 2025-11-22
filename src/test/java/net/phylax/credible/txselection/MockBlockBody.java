package net.phylax.credible.txselection;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.Withdrawal;

public class MockBlockBody implements BlockBody {

    private final BlockBody mockBlockBody;
    private final int transactionCount;

    public MockBlockBody(int transactionCount) {
        this.transactionCount = transactionCount;
        this.mockBlockBody = mock(BlockBody.class);
        setupDefaultMockBehaviors();
    }

    public MockBlockBody() {
        this(0);
    }

    private void setupDefaultMockBehaviors() {
        // Mock transactions list with the specified size
        @SuppressWarnings("unchecked")
        List<Transaction> mockTransactions = mock(List.class);
        when(mockTransactions.size()).thenReturn(this.transactionCount);
        doReturn(mockTransactions).when(mockBlockBody).getTransactions();

        // Mock empty ommers and withdrawals
        doReturn(List.of()).when(mockBlockBody).getOmmers();
        doReturn(Optional.empty()).when(mockBlockBody).getWithdrawals();
    }

    @Override
    public List<? extends Transaction> getTransactions() {
        return mockBlockBody.getTransactions();
    }

    @Override
    public List<? extends BlockHeader> getOmmers() {
        return mockBlockBody.getOmmers();
    }

    @Override
    public Optional<? extends List<? extends Withdrawal>> getWithdrawals() {
        return mockBlockBody.getWithdrawals();
    }

    public BlockBody getMock() {
        return mockBlockBody;
    }
}
