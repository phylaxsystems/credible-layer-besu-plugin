package net.phylax.credible;

import java.lang.reflect.InvocationTargetException;

import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

/**
 * Resolves services from the upstream Lineth runtime without compile-time linkage.
 * Maven Central only publishes {@code build.linea:sequencer-interfaces:0.0.1}, which
 * contains the old {@code linea.*} packages; remove this adapter once a {@code lineth.*}
 * interface artifact is available.
 */
public final class LineaRuntimeCompatibility {
    private LineaRuntimeCompatibility() {}

    @FunctionalInterface
    public interface ChainSecurityPolicy {
        boolean shallForceIncludeTransaction(TransactionEvaluationContext txContext);
    }

    public record Bindings(
        ChainSecurityPolicy chainSecurityPolicy,
        TransactionSelectionResult chainSecurityRuleViolated
    ) {}

    public static Bindings resolve(ServiceManager serviceManager) {
        ClassLoader classLoader = LineaRuntimeCompatibility.class.getClassLoader();
        try {
            Class<? extends BesuService> policyClass = Class
                .forName("lineth.security.ChainSecurityPolicy", false, classLoader)
                .asSubclass(BesuService.class);
            var policyService = serviceManager.getService(policyClass);
            if (policyService.isEmpty()) {
                throw new IllegalStateException(
                    "Failed to obtain ChainSecurityPolicy from the Lineth runtime");
            }

            var policyMethod = policyClass.getMethod(
                "shallForceIncludeTransaction", TransactionEvaluationContext.class);
            var resultClass = Class.forName(
                "lineth.txselection.LineaTransactionSelectionResult", false, classLoader);
            var violationResult = (TransactionSelectionResult) resultClass
                .getField("CHAIN_SECURITY_RULE_VIOLATED")
                .get(null);

            return new Bindings(
                txContext -> invokePolicy(policyService.get(), policyMethod, txContext),
                violationResult);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("Incompatible Lineth runtime API", e);
        }
    }

    private static boolean invokePolicy(
        BesuService policy,
        java.lang.reflect.Method method,
        TransactionEvaluationContext txContext
    ) {
        try {
            return (boolean) method.invoke(policy, txContext);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("ChainSecurityPolicy invocation failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ChainSecurityPolicy invocation failed", e);
        }
    }
}
