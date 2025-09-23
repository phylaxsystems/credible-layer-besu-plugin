package net.phylax.credible.metrics;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

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
            "Time taken to evaluate transaction pre-processing in seconds"
        );
        
        postProcessingTimer = metricsSystem.createLabelledTimer(
            CredibleMetricsCategory.PLUGIN,
            "postprocessing_time",
            "Time taken to evaluate transaction post-processing in seconds"
        );

        pollingTimer = metricsSystem.createLabelledTimer(
            CredibleMetricsCategory.PLUGIN,
            "polling_time",
            "Time it took for the getTransactions call to return in seconds"
        );
        
        timeoutCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "timeout_counter",
            "Number of timeouts"
        );
        
        errorCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "error_counter",
            "Number of errors"
        );

        transactionCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "transaction_counter",
            "Number of transactions"
        );

        invalidationCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "invalidation_counter",
            "Number of invalidations"
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
