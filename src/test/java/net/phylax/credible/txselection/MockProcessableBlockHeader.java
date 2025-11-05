package net.phylax.credible.txselection;

import static org.mockito.Mockito.*;

import java.util.Optional;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Mock implementation of ProcessableBlockHeader for testing purposes.
 */
public class MockProcessableBlockHeader implements ProcessableBlockHeader {
    private final ProcessableBlockHeader mockHeader;
    private final Long blockNumber;
    
    /**
     * Constructor using Mockito to create the mock
     */
    public MockProcessableBlockHeader(Long blockNumber) {
        this.mockHeader = mock(ProcessableBlockHeader.class);
        this.blockNumber = blockNumber;
        setupDefaultMockBehaviors();
    }
    
    public MockProcessableBlockHeader(ProcessableBlockHeader header) {
        this.mockHeader = header;
        this.blockNumber = header.getNumber();
    }
    
    private void setupDefaultMockBehaviors() {
        Hash mockParentHash = mock(Hash.class);
        when(mockParentHash.toHexString()).thenReturn("0x"+this.blockNumber);
        doReturn(mockParentHash).when(mockHeader).getParentHash();

        doReturn(this.blockNumber).when(mockHeader).getNumber();
        doReturn(System.currentTimeMillis() / 1000).when(mockHeader).getTimestamp();
        
        doReturn(30000000L).when(mockHeader).getGasLimit();
        lenient().when(mockHeader.getDifficulty()).thenReturn(Wei.of(10L));

        Address mockCoinbase = mock(Address.class);
        when(mockCoinbase.toHexString()).thenReturn("0x0000000000000000000000000000000000000000");
        doReturn(mockCoinbase).when(mockHeader).getCoinbase();
        
        doReturn(Optional.of(Wei.of(1000000000L))).when(mockHeader).getBaseFee();
    }

    @Override
    public Hash getParentHash() {
        return mockHeader.getParentHash();
    }
    
    @Override
    public Address getCoinbase() {
        return mockHeader.getCoinbase();
    }
    
    @Override
    public long getNumber() {
        return mockHeader.getNumber();
    }
    
    @Override
    public long getGasLimit() {
        return mockHeader.getGasLimit();
    }

    @Override
    public long getTimestamp() {
        return mockHeader.getTimestamp();
    }
    
    @Override
    public Optional<Wei> getBaseFee() {
        return (Optional) mockHeader.getBaseFee();
    }
    
    public ProcessableBlockHeader getMock() {
        return mockHeader;
    }

    @Override
    public Quantity getDifficulty() {
        return mockHeader.getDifficulty();
    }

    @Override
    public Optional<? extends Bytes32> getParentBeaconBlockRoot() {
        throw new UnsupportedOperationException("Unimplemented method 'getParentBeaconBlockRoot'");
    }
}