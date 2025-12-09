package net.phylax.credible.metrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

/**
 * Registry for Credible Metrics. Creates and registers all metrics
 * regarding the credible layer processing.
 */
public class CredibleMetricsRegistry {
    private final LabelledMetric<OperationTimer> pollingTimer;
    private final LabelledMetric<Counter> timeoutCounter;
    private final LabelledMetric<Counter> iterationTimeoutCounter;
    private final LabelledMetric<Counter> errorCounter;
    private final LabelledMetric<Counter> transactionCounter;
    private final LabelledMetric<Counter> invalidationCounter;
    private final LabelledMetric<Counter> reorgRequestCounter;
    private final LabelledMetric<Counter> sidecarRpcCounter;
    private final LabelledMetric<Counter> streamAckRetryCounter;
    private final LabelledMetric<Histogram> streamAckLatency;

    // Transport request duration histograms
    private final LabelledMetric<Histogram> transportRequestDuration;

    // Transaction processing duration histograms
    private final LabelledMetric<Histogram> preProcessingDuration;
    private final LabelledMetric<Histogram> postProcessingDuration;

    private final AtomicBoolean activeTransportsGaugeRegistered = new AtomicBoolean(false);

    private final MetricsSystem metricsSystem;

    public CredibleMetricsRegistry(final MetricsSystem metricsSystem) {
        this.metricsSystem = metricsSystem;

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

        iterationTimeoutCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "iteration_timeout_counter",
            "Number of iterations that exceeded the configured timeout"
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

        reorgRequestCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "reorg_request_counter",
            "Number of reorg requests"
        );

        sidecarRpcCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "sidecar_rpc_total",
            "Total RPC calls made to the Credible sidecar",
            "method");

        streamAckRetryCounter = metricsSystem.createLabelledCounter(
            CredibleMetricsCategory.PLUGIN,
            "stream_ack_retry_total",
            "Number of stream event retries due to ack timeout"
        );

        // Stream ack latency histogram with fine-grained buckets in seconds
        // Buckets: 0.1ms, 0.5ms, 1ms, 2ms, 5ms, 10ms, 20ms, 50ms, 100ms, 200ms, 500ms
        streamAckLatency = metricsSystem.createLabelledHistogram(
            CredibleMetricsCategory.PLUGIN,
            "stream_ack_latency_seconds",
            "Distribution of time between sending an event and receiving its ack",
            new double[]{0.0001, 0.0005, 0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5}
        );

        // Transport request duration with fine-grained buckets in seconds
        // Buckets: 0.05ms, 0.1ms, 0.5ms, 1ms, 2ms, 5ms, 10ms, 20ms, 50ms, 100ms, 200ms, 500ms
        transportRequestDuration = metricsSystem.createLabelledHistogram(
            CredibleMetricsCategory.PLUGIN,
            "transport_request_duration_seconds",
            "Distribution of transport request durations in seconds",
            new double[]{0.00005, 0.0001, 0.0005, 0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5},
            "method", "transport_type", "status");

        // Transaction processing duration histograms
        preProcessingDuration = metricsSystem.createLabelledHistogram(
            CredibleMetricsCategory.PLUGIN,
            "preprocessing_duration_seconds",
            "Distribution of transaction pre-processing durations in seconds",
            new double[]{0.00005, 0.0001, 0.0005, 0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5},
            "status");

        postProcessingDuration = metricsSystem.createLabelledHistogram(
            CredibleMetricsCategory.PLUGIN,
            "postprocessing_duration_seconds",
            "Distribution of transaction post-processing durations in seconds",
            new double[]{0.00005, 0.0001, 0.0005, 0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5},
            "status");
    }

    public LabelledMetric<OperationTimer> getPollingTimer() {
        return pollingTimer;
    }
    
    public LabelledMetric<Counter> getTimeoutCounter() {
        return timeoutCounter;
    }

    public LabelledMetric<Counter> getIterationTimeoutCounter() {
        return iterationTimeoutCounter;
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

    public LabelledMetric<Counter> getReorgRequestCounter() {
        return reorgRequestCounter;
    }

    public LabelledMetric<Counter> getSidecarRpcCounter() {
        return sidecarRpcCounter;
    }

    public LabelledMetric<Counter> getStreamAckRetryCounter() {
        return streamAckRetryCounter;
    }

    public LabelledMetric<Histogram> getStreamAckLatency() {
        return streamAckLatency;
    }

    public LabelledMetric<Histogram> getTransportRequestDuration() {
        return transportRequestDuration;
    }

    public LabelledMetric<Histogram> getPreProcessingDuration() {
        return preProcessingDuration;
    }

    public LabelledMetric<Histogram> getPostProcessingDuration() {
        return postProcessingDuration;
    }

    public void registerActiveTransportsGauge(final IntSupplier supplier) {
        if (activeTransportsGaugeRegistered.compareAndSet(false, true)) {
          metricsSystem.createIntegerGauge(
            CredibleMetricsCategory.PLUGIN,
              "active_sidecar_transports",
              "Number of sidecar transports currently marked as active",
              supplier);
        }
      }
}
