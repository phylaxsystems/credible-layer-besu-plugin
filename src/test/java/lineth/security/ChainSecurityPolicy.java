package lineth.security;

import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

public interface ChainSecurityPolicy extends BesuService {
    boolean shallForceIncludeTransaction(TransactionEvaluationContext txContext);
}
