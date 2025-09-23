package net.phylax.credible.metrics;

import java.util.Optional;

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

/**
 * Besu MetricsSystem Category
 * 
 * Needs to implement MetricCategory from Besu in order to be registered
 */
public enum CredibleMetricsCategory implements MetricCategory {
    PLUGIN;

    public static final String NAME = "credible";

    @Override
    public String getName() {
        return NAME;
    }

    // NOTE: metrics are already prefixed with the above
    @Override
    public Optional<String> getApplicationPrefix() {
        return Optional.empty();
    }
}
