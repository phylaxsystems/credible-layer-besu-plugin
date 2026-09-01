package lineth.txselection;

import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

public final class LineaTransactionSelectionResult {
    public static final TransactionSelectionResult CHAIN_SECURITY_RULE_VIOLATED =
        TransactionSelectionResult.SELECTED;

    private LineaTransactionSelectionResult() {}
}
