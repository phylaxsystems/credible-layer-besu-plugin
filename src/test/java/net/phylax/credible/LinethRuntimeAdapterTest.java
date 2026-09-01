package net.phylax.credible;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;
import org.junit.jupiter.api.Test;

class LinethRuntimeAdapterTest {
    @Test
    void resolvesLinethRuntimeServices() {
        var serviceManager = mock(ServiceManager.class);
        var policy = mock(lineth.security.ChainSecurityPolicy.class);
        var txContext = mock(TransactionEvaluationContext.class);
        when(serviceManager.getService(lineth.security.ChainSecurityPolicy.class))
            .thenReturn(Optional.of(policy));
        when(policy.shallForceIncludeTransaction(txContext)).thenReturn(true);

        var bindings = LinethRuntimeAdapter.resolve(serviceManager);

        assertTrue(bindings.chainSecurityPolicy().shallForceIncludeTransaction(txContext));
        assertSame(
            lineth.txselection.LineaTransactionSelectionResult.CHAIN_SECURITY_RULE_VIOLATED,
            bindings.chainSecurityRuleViolated());
    }
}
