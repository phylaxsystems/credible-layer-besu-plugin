package net.phylax.credible.txselection;

import static org.mockito.Mockito.*;

import java.util.Optional;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.LogsBloomFilter;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.apache.tuweni.bytes.Bytes;

public class MockBlockHeader implements BlockHeader {
    
    private final BlockHeader mockHeader;
    private final Long blockNumber;

    public MockBlockHeader(Long blockNumber) {
        this.blockNumber = blockNumber;
        this.mockHeader = mock(BlockHeader.class);
        setupDefaultMockBehaviors();
    }

    public MockBlockHeader(BlockHeader header) {
        this.mockHeader = header;
        this.blockNumber = header.getNumber();
    }
    
    private void setupDefaultMockBehaviors() {
        doReturn(Hash.fromHexString(String.format("0x%064x", this.blockNumber))).when(mockHeader).getBlockHash();
        doReturn(Hash.fromHexString("0x9876543210fedcba9876543210fedcba9876543210fedcba9876543210fedcba")).when(mockHeader).getParentHash();
        
        doReturn(this.blockNumber).when(mockHeader).getNumber();
        doReturn(System.currentTimeMillis() / 1000).when(mockHeader).getTimestamp();
        
        doReturn(30000000L).when(mockHeader).getGasLimit();
        doReturn(15000000L).when(mockHeader).getGasUsed();
        lenient().when(mockHeader.getDifficulty()).thenReturn(Wei.of(10L));

        doReturn(Address.ZERO).when(mockHeader).getCoinbase();

        doReturn(Optional.of(Wei.of(1000000000L))).when(mockHeader).getBaseFee();

        doReturn(Bytes.EMPTY).when(mockHeader).getExtraData();
        
        lenient().when(mockHeader.getStateRoot()).thenReturn(Hash.ZERO);
        doReturn(Hash.ZERO).when(mockHeader).getReceiptsRoot();
        doReturn(Hash.ZERO).when(mockHeader).getTransactionsRoot();

        doReturn(LogsBloomFilter.empty()).when(mockHeader).getLogsBloom();
        
        doReturn(Hash.ZERO).when(mockHeader).getMixHash();
        doReturn(0L).when(mockHeader).getNonce();

        doReturn(Optional.empty()).when(mockHeader).getBlobGasUsed();
        doReturn(Optional.empty()).when(mockHeader).getExcessBlobGas();
        
        doReturn(Optional.empty()).when(mockHeader).getWithdrawalsRoot();

        doReturn(Optional.empty()).when(mockHeader).getParentBeaconBlockRoot();
    }

    @Override
    public Hash getParentHash() {
        return mockHeader.getParentHash();
    }
    
    @Override
    public Hash getBlockHash() {
        return mockHeader.getBlockHash();
    }
    
    @Override
    public Address getCoinbase() {
        return mockHeader.getCoinbase();
    }
    
    @Override
    public Hash getStateRoot() {
        return mockHeader.getStateRoot();
    }
    
    @Override
    public Hash getTransactionsRoot() {
        return mockHeader.getTransactionsRoot();
    }
    
    @Override
    public Hash getReceiptsRoot() {
        return mockHeader.getReceiptsRoot();
    }
    
    @Override
    public LogsBloomFilter getLogsBloom() {
        return mockHeader.getLogsBloom();
    }
    
    @Override
    public Quantity getDifficulty() {
        return mockHeader.getDifficulty();
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
    public long getGasUsed() {
        return mockHeader.getGasUsed();
    }
    
    @Override
    public long getTimestamp() {
        return mockHeader.getTimestamp();
    }
    
    @Override
    public Bytes getExtraData() {
        return mockHeader.getExtraData();
    }
    
    @Override
    public Hash getMixHash() {
        return mockHeader.getMixHash();
    }
    
    @Override
    public long getNonce() {
        return mockHeader.getNonce();
    }
    
    @Override
    public Optional<Wei> getBaseFee() {
        return (Optional) mockHeader.getBaseFee();
    }
    
    @Override
    public Optional<? extends Hash> getWithdrawalsRoot() {
        return (Optional) mockHeader.getWithdrawalsRoot();
    }
    
    @Override
    public Optional<? extends Long> getBlobGasUsed() {
        return (Optional) mockHeader.getBlobGasUsed();
    }
    
    @Override
    public Optional<? extends Quantity> getExcessBlobGas() {
        return (Optional) mockHeader.getExcessBlobGas();
    }
    
    @Override
    public Optional<? extends org.apache.tuweni.bytes.Bytes32> getParentBeaconBlockRoot() {
        return (Optional) mockHeader.getParentBeaconBlockRoot();
    }
    
    public BlockHeader getMock() {
        return mockHeader;
    }

    @Override
    public Hash getOmmersHash() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getOmmersHash'");
    }

    @Override
    public Optional<? extends Hash> getRequestsHash() {
        return Optional.empty();
    }

    @Override
    public Optional<? extends Hash> getBalHash() {
        return Optional.empty();
    }
}
