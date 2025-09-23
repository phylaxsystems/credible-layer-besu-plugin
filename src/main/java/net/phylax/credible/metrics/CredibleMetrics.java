package net.phylax.credible.metrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

/** Helper that centralises metric registration and recording for the Credible plugin. */
public class CredibleMetrics {
  private static final double[] RPC_LATENCY_BUCKETS_SECONDS =
      new double[] {0.01D, 0.025D, 0.05D, 0.1D, 0.25D, 0.5D, 1D, 2D, 5D, 10D};

  private final MetricsSystem metricsSystem;
  private final LabelledMetric<Counter> sidecarRpcCounter;
  private final LabelledMetric<Histogram> sidecarRpcLatency;
  private final Counter txPreProcessingCounter;
  private final LabelledMetric<Counter> txPostExclusionCounter;
  private final Counter reorgRequestCounter;

  private final AtomicBoolean activeTransportsGaugeRegistered = new AtomicBoolean(false);
  private final AtomicBoolean pendingRequestsGaugeRegistered = new AtomicBoolean(false);

  public CredibleMetrics(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    this.sidecarRpcCounter =
        metricsSystem.createLabelledCounter(
            CredibleMetricCategory.SIDE_CAR,
            "sidecar_rpc_total",
            "Total RPC calls made to the Credible sidecar",
            "method",
            "status");
    this.sidecarRpcLatency =
        metricsSystem.createLabelledHistogram(
            CredibleMetricCategory.SIDE_CAR,
            "sidecar_rpc_latency_seconds",
            "Latency in seconds observed when calling Credible sidecar RPCs",
            RPC_LATENCY_BUCKETS_SECONDS,
            "method");
    this.txPreProcessingCounter =
        metricsSystem.createCounter(
            CredibleMetricCategory.SIDE_CAR,
            "tx_preprocessing_total",
            "Transactions forwarded to the Credible sidecar during pre-processing");
    this.txPostExclusionCounter =
        metricsSystem.createLabelledCounter(
            CredibleMetricCategory.SIDE_CAR,
            "tx_postprocessing_excluded_total",
            "Transactions excluded by the Credible sidecar during post-processing",
            "status");
    this.reorgRequestCounter =
        metricsSystem.createCounter(
            CredibleMetricCategory.SIDE_CAR,
            "reorg_requests_total",
            "Re-org notifications emitted towards the Credible sidecar");
  }

  public void recordSidecarRpc(final String method, final boolean success, final long durationNanos) {
    final double durationSeconds = durationNanos / 1_000_000_000.0D;
    sidecarRpcCounter.labels(method, success ? "success" : "failure").inc();
    sidecarRpcLatency.labels(method).observe(durationSeconds);
  }

  public void recordTxPreProcessing() {
    txPreProcessingCounter.inc();
  }

  public void recordTxPostExclusion(final String status) {
    txPostExclusionCounter.labels(status).inc();
  }

  public void recordReorgRequest() {
    reorgRequestCounter.inc();
  }

  public void registerActiveTransportsGauge(final IntSupplier supplier) {
    if (activeTransportsGaugeRegistered.compareAndSet(false, true)) {
      metricsSystem.createIntegerGauge(
          CredibleMetricCategory.SIDE_CAR,
          "active_sidecar_transports",
          "Number of sidecar transports currently marked as active",
          supplier);
    }
  }

  public void registerPendingRequestsGauge(final IntSupplier supplier) {
    if (pendingRequestsGaugeRegistered.compareAndSet(false, true)) {
      metricsSystem.createIntegerGauge(
          CredibleMetricCategory.SIDE_CAR,
          "pending_transaction_requests",
          "Outstanding transaction hashes awaiting Credible sidecar responses",
          supplier);
    }
  }
}
