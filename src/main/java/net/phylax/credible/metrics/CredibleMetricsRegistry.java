package net.phylax.credible.metrics;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

/**
 * Registry for Credible Metrics. Creates and registers all metrics
 * regarding the credible layer processing.
 */
public class CredibleMetricsRegistry {
    private final LabelledMetric<OperationTimer> preProcessingTimer;
    private final LabelledMetric<OperationTimer> postProcessingTimer;
    private final LabelledMetric<OperationTimer> pollingTimer;
    private final LabelledMetric<Counter> timeoutCounter;
    private final LabelledMetric<Counter> errorCounter;
    private final LabelledMetric<Counter> transactionCounter;
    private final LabelledMetric<Counter> invalidationCounter;
    
    public CredibleMetricsRegistry(final MetricsSystem metricsSystem) {
        // Initialize all metrics
        preProcessingTimer = metricsSystem.createLabelledTimer(
            CredibleMetricsCategory.PLUGIN,
            "preprocessing_time",
            "Time taken to evaluate transaction pre-processing phase in seconds"
        );
        
        postProcessingTimer = metricsSystem.createLabelledTimer(
            CredibleMetricsCategory.PLUGIN,
            "postprocessing_time",
            "Time taken to evaluate transaction post-processingphase in seconds"
        );

        pollingTimer = metricsSystem.createLabelledTimer(
            CredibleMetricsCategory.PLUGIN,
            "polling_time",
            "Time it took for the getTransactions call to return in seconds"
        );
        
        timeoutCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "timeout_counter",
            "Number of timeout exceptions"
        );
        
        errorCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "error_counter",
            "Number of general errors and exceptions caught by the plugin"
        );

        transactionCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "transaction_counter",
            "Number of transactions that are passed to the plugin"
        );

        invalidationCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "invalidation_counter",
            "Number of successful assertion invalidations"
        );
    }
    
    // Getters
    public LabelledMetric<OperationTimer> getPreProcessingTimer() {
        return preProcessingTimer;
    }
    
    public LabelledMetric<OperationTimer> getPostProcessingTimer() {
        return postProcessingTimer;
    }

    public LabelledMetric<OperationTimer> getPollingTimer() {
        return pollingTimer;
    }
    
    public LabelledMetric<Counter> getTimeoutCounter() {
        return timeoutCounter;
    }
    
    public LabelledMetric<Counter> getErrorCounter() {
        return errorCounter;
    }

    public LabelledMetric<Counter> getTransactionCounter() {
        return transactionCounter;
    }

    public LabelledMetric<Counter> getInvalidationCounter() {
        return invalidationCounter;
    }
}
