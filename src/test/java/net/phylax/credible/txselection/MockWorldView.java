package net.phylax.credible.txselection;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;

public class MockWorldView implements WorldView {
    @Override
    public Account get(Address address) {
        throw new UnsupportedOperationException("Unimplemented method 'get'");
    }
}
