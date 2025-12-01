package net.phylax.credible.transport.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;
import net.phylax.credible.metrics.CredibleMetricsRegistry;

/**
 * gRPC client interceptor that logs the start and completion of RPC calls
 * with precise timing information and records metrics.
 */
@Slf4j
public class LoggingClientInterceptor implements ClientInterceptor {
    private final CredibleMetricsRegistry metricsRegistry;

    /**
     * Create a LoggingClientInterceptor with metrics recording
     */
    public LoggingClientInterceptor(CredibleMetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    /**
     * Helper method to record request duration in histogram
     */
    private void recordRequestDuration(long startTimeNanos, String method, String status) {
        double durationSeconds = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
        metricsRegistry.getTransportRequestDuration()
            .labels(method, "grpc", status)
            .observe(durationSeconds);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            private long startTimeNanos;
            private String methodName;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                startTimeNanos = System.nanoTime();
                methodName = method.getFullMethodName();

                log.debug("[gRPC-START] {} at {}ns", methodName, startTimeNanos);

                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(io.grpc.Status status, Metadata trailers) {
                        long closeTime = System.nanoTime();
                        long elapsedUs = (closeTime - startTimeNanos) / 1_000;

                        if (status.isOk()) {
                            log.debug("[gRPC-CLOSE] {} - completed successfully after {}us", methodName, elapsedUs);
                            recordRequestDuration(startTimeNanos, methodName, "success");
                        } else {
                            log.warn("[gRPC-CLOSE] {} - failed with {} after {}us: {}",
                                methodName, status.getCode(), elapsedUs, status.getDescription());
                            recordRequestDuration(startTimeNanos, methodName, "error");
                        }

                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
