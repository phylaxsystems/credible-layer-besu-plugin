package net.phylax.credible.metrics;

import java.util.Optional;

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public enum CredibleMetricsCategory implements MetricCategory {
    PLUGIN;

    public static final String NAME = "credible";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Optional<String> getApplicationPrefix() {
        return Optional.empty();
    }
}
