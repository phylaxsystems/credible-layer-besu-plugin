package net.phylax.credible.metrics;

import java.util.Set;
import java.util.function.IntSupplier;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedSummary;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import com.google.common.cache.Cache;

public class SimpleMockMetricsSystem implements MetricsSystem {
    
    private int timerCreationCount = 0;
    private int counterCreationCount = 0;
    private int gaugeCreationCount = 0;
    
    @Override
    public LabelledMetric<Counter> createLabelledCounter(
            MetricCategory category, String name, String help, String... labelNames) {
        counterCreationCount++;
        return new MockLabelledCounter();
    }
    
    @Override
    public LabelledMetric<OperationTimer> createLabelledTimer(
            MetricCategory category, String name, String help, String... labelNames) {
        timerCreationCount++;
        return new MockLabelledTimer();
    }
    
    @Override
    public void createIntegerGauge(
            MetricCategory category, String name, String help, IntSupplier supplier) {
        gaugeCreationCount++;
    }
    
    public int getTimerCreationCount() { return timerCreationCount; }
    public int getCounterCreationCount() { return counterCreationCount; }
    public int getGaugeCreationCount() { return gaugeCreationCount; }
    
    // Mock implementations of metrics
    private static class MockLabelledCounter implements LabelledMetric<Counter> {
        private final MockCounter counter = new MockCounter();
        
        @Override
        public Counter labels(String... labelValues) {
            return counter;
        }
    }
    
    private static class MockLabelledTimer implements LabelledMetric<OperationTimer> {
        private final MockOperationTimer timer = new MockOperationTimer();
        
        @Override
        public OperationTimer labels(String... labelValues) {
            return timer;
        }
    }
    
    private static class MockCounter implements Counter {
        private long count = 0;
        
        @Override
        public void inc() {
            count++;
        }
    
        @Override
        public void inc(long amount) {
            count += amount;
        }
    }
    
    private static class MockOperationTimer implements OperationTimer {
        private int startCount = 0;

        @Override
        public TimingContext startTimer() {
            startCount++;
            return () -> { return startCount; };
        }
    }

    private static class MockLabelledHistogram implements LabelledMetric<Histogram> {
        private final MockHistogram histogram = new MockHistogram();

        @Override
        public Histogram labels(String... labelValues) {
            return histogram;
        }
    }

    private static class MockHistogram implements Histogram {
        @Override
        public void observe(double amount) {
            // No-op for testing
        }
    }

    // NOTE: unused methods
    @Override
    public void createGuavaCacheCollector(MetricCategory arg0, String arg1, Cache<?, ?> arg2) {
        throw new UnsupportedOperationException("Unimplemented method 'createGuavaCacheCollector'");
    }

    @Override
    public LabelledMetric<Histogram> createLabelledHistogram(MetricCategory arg0, String arg1, String arg2,
            double[] arg3, String... arg4) {
        return new MockLabelledHistogram();
    }

    @Override
    public LabelledSuppliedMetric createLabelledSuppliedCounter(MetricCategory arg0, String arg1, String arg2,
            String... arg3) {
        throw new UnsupportedOperationException("Unimplemented method 'createLabelledHistogram'");
    }

    @Override
    public LabelledSuppliedMetric createLabelledSuppliedGauge(MetricCategory arg0, String arg1, String arg2,
            String... arg3) {
        throw new UnsupportedOperationException("Unimplemented method 'createLabelledSuppliedGauge'");
    }

    @Override
    public LabelledSuppliedSummary createLabelledSuppliedSummary(MetricCategory arg0, String arg1, String arg2,
            String... arg3) {
        throw new UnsupportedOperationException("Unimplemented method 'createLabelledSuppliedSummary'");
    }

    @Override
    public LabelledMetric<OperationTimer> createSimpleLabelledTimer(MetricCategory arg0, String arg1, String arg2,
            String... arg3) {
        return new MockLabelledTimer();
    }

    @Override
    public Set<MetricCategory> getEnabledCategories() {
        throw new UnsupportedOperationException("Unimplemented method 'getEnabledCategories'");
    }
}

