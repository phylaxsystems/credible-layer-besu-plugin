package net.phylax.credible.metrics;

import java.util.Optional;

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

/** Metric category for Credible Layer plugin metrics. */
public enum CredibleMetricCategory implements MetricCategory {
  SIDE_CAR;

  @Override
  public String getName() {
    return "credible_sidecar";
  }

  @Override
  public Optional<String> getApplicationPrefix() {
    return Optional.of("credible_");
  }
}
