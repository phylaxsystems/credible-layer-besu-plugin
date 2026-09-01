package net.phylax.credible;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

/** Resolves Linea runtime services across the linea.* to lineth.* package rename. */
public final class LineaRuntimeCompatibility {
    private static final List<String> RUNTIME_NAMESPACES = List.of("lineth", "linea");

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

        for (String namespace : RUNTIME_NAMESPACES) {
            String policyClassName = namespace + ".security.ChainSecurityPolicy";
            try {
                Class<? extends BesuService> policyClass =
                    Class.forName(policyClassName, false, classLoader).asSubclass(BesuService.class);
                var policyService = serviceManager.getService(policyClass);
                if (policyService.isEmpty()) {
                    continue;
                }

                var policyMethod = policyClass.getMethod(
                    "shallForceIncludeTransaction", TransactionEvaluationContext.class);
                var resultClass = Class.forName(
                    namespace + ".txselection.LineaTransactionSelectionResult", false, classLoader);
                var violationResult = (TransactionSelectionResult) resultClass
                    .getField("CHAIN_SECURITY_RULE_VIOLATED")
                    .get(null);

                return new Bindings(
                    txContext -> invokePolicy(policyService.get(), policyMethod, txContext),
                    violationResult);
            } catch (ClassNotFoundException e) {
                // Try the other Linea runtime namespace.
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalStateException(
                    "Incompatible Linea runtime API for namespace " + namespace, e);
            }
        }

        throw new IllegalStateException(
            "Failed to obtain ChainSecurityPolicy from the Linea runtime");
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
