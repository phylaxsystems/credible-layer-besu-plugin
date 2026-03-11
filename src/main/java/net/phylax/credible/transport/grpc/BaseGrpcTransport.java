package net.phylax.credible.transport.grpc;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;

import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;
import lombok.extern.slf4j.Slf4j;


/**
 * Base class for gRPC transports providing shared channel infrastructure,
 * lifecycle management, and utility methods.
 */
@Slf4j
public abstract class BaseGrpcTransport {
    protected final ManagedChannel channel;
    protected final long deadlineMillis;

    protected BaseGrpcTransport(ManagedChannel channel, long deadlineMillis) {
        this.channel = channel;
        this.deadlineMillis = deadlineMillis;
    }

    /**
     * Reset the channel's backoff to force immediate reconnection.
     */
    protected void resetChannelBackoff() {
        channel.resetConnectBackoff();
    }

    /**
     * Get the current connectivity state of the channel.
     *
     * @param requestConnection if true, the channel will try to connect if idle
     * @return the current connectivity state
     */
    protected ConnectivityState getChannelState(boolean requestConnection) {
        return channel.getState(requestConnection);
    }

    /**
     * Shutdown the underlying gRPC channel gracefully.
     */
    public void shutdownChannel() {
        channel.shutdown();
    }

    /**
     * Extract a meaningful error message from gRPC exceptions.
     */
    protected String getErrorMessage(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            StatusRuntimeException sre = (StatusRuntimeException) t;
            Status status = sre.getStatus();
            return String.format("%s: %s", status.getCode(), status.getDescription());
        }
        return t.getMessage();
    }

    /**
     * Builder for creating production-grade gRPC ManagedChannels with
     * TCP_NODELAY, keepalive, flow control, and optional interceptors.
     */
    public static class ChannelBuilder {
        private String host = "localhost";
        private int port = 50051;
        private final List<ClientInterceptor> interceptors = new ArrayList<>();

        public ChannelBuilder host(String host) {
            this.host = host;
            return this;
        }

        public ChannelBuilder port(int port) {
            this.port = port;
            return this;
        }

        public ChannelBuilder interceptor(ClientInterceptor interceptor) {
            this.interceptors.add(interceptor);
            return this;
        }

        public ManagedChannel build() {
            SocketFactory flushingSocketFactory = new SocketFactory() {
                private final SocketFactory delegate = SocketFactory.getDefault();

                @Override
                public Socket createSocket() throws IOException {
                    Socket socket = delegate.createSocket();
                    socket.setTcpNoDelay(true);
                    return socket;
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    Socket socket = delegate.createSocket(host, port);
                    socket.setTcpNoDelay(true);
                    return socket;
                }

                @Override
                public Socket createSocket(String host, int port,
                        java.net.InetAddress localHost, int localPort) throws IOException {
                    Socket socket = delegate.createSocket(host, port, localHost, localPort);
                    socket.setTcpNoDelay(true);
                    return socket;
                }

                @Override
                public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
                    Socket socket = delegate.createSocket(host, port);
                    socket.setTcpNoDelay(true);
                    return socket;
                }

                @Override
                public Socket createSocket(java.net.InetAddress address, int port,
                        java.net.InetAddress localAddress, int localPort) throws IOException {
                    Socket socket = delegate.createSocket(address, port, localAddress, localPort);
                    socket.setTcpNoDelay(true);
                    return socket;
                }
            };

            OkHttpChannelBuilder channelBuilder = OkHttpChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .socketFactory(flushingSocketFactory)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .flowControlWindow(16 * 1024 * 1024)
                .maxInboundMetadataSize(8192)
                .maxInboundMessageSize(1 * 1024 * 1024);

            for (ClientInterceptor interceptor : interceptors) {
                channelBuilder.intercept(interceptor);
            }

            return channelBuilder.build();
        }
    }
}
