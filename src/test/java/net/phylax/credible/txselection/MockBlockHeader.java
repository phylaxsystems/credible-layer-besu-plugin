package net.phylax.credible.txselection;

import static org.mockito.Mockito.*;

import java.util.Optional;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
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
        Hash mockHash = mock(Hash.class);
        when(mockHash.toHexString()).thenReturn("0x"+this.blockNumber);
        doReturn(mockHash).when(mockHeader).getBlockHash();
        
        Hash mockParentHash = mock(Hash.class);
        when(mockParentHash.toHexString()).thenReturn("0x9876543210fedcba9876543210fedcba9876543210fedcba9876543210fedcba");
        doReturn(mockParentHash).when(mockHeader).getParentHash();
        
        doReturn(this.blockNumber).when(mockHeader).getNumber();
        doReturn(System.currentTimeMillis() / 1000).when(mockHeader).getTimestamp();
        
        doReturn(30000000L).when(mockHeader).getGasLimit();
        doReturn(15000000L).when(mockHeader).getGasUsed();
        lenient().when(mockHeader.getDifficulty()).thenReturn(Wei.of(10L));

        Address mockCoinbase = mock(Address.class);
        when(mockCoinbase.toHexString()).thenReturn("0x0000000000000000000000000000000000000000");
        doReturn(mockCoinbase).when(mockHeader).getCoinbase();

        doReturn(Optional.of(Wei.of(1000000000L))).when(mockHeader).getBaseFee();

        doReturn(Bytes.EMPTY).when(mockHeader).getExtraData();
        
        Hash mockStateRootHash = mock(Hash.class);
        lenient().when(mockHeader.getStateRoot()).thenReturn(mockStateRootHash);
        
        Hash mockReceiptRootHash = mock(Hash.class);
        doReturn(mockReceiptRootHash).when(mockHeader).getReceiptsRoot();
        
        Hash mockTransactionsRoot = mock(Hash.class);
        doReturn(mockTransactionsRoot).when(mockHeader).getTransactionsRoot();

        doReturn(Bytes.wrap(new byte[256])).when(mockHeader).getLogsBloom();
        
        Hash mockMixHash = mock(Hash.class);
        doReturn(mockMixHash).when(mockHeader).getMixHash();
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
    public Bytes getLogsBloom() {
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
    public Optional<Hash> getWithdrawalsRoot() {
        return (Optional) mockHeader.getWithdrawalsRoot();
    }
    
    @Override
    public Optional<Long> getBlobGasUsed() {
        return (Optional) mockHeader.getBlobGasUsed();
    }
    
    @Override
    public Optional<Quantity> getExcessBlobGas() {
        return (Optional) mockHeader.getExcessBlobGas();
    }
    
    @Override
    public Optional<Hash> getParentBeaconBlockRoot() {
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRequestsHash'");
    }

    @Override
    public Optional<? extends Hash> getBalHash() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBalHash'");
    }
}