package com.veryal.access.core.metrics;

import com.veryal.common.observability.MetricNames;
import com.veryal.protocol.api.Command;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gateway 指标。指标名统一来自 {@link MetricNames}。
 */
@Component
public class GatewayMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final Counter connectionsCreated;
    private final Counter connectionsClosed;
    private final Counter heartbeatTimeouts;
    private final Counter errors;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.connectionsCreated = Counter.builder(MetricNames.CONNECTIONS_CREATED).register(registry);
        this.connectionsClosed = Counter.builder(MetricNames.CONNECTIONS_CLOSED).register(registry);
        this.heartbeatTimeouts = Counter.builder(MetricNames.HEARTBEAT_TIMEOUT).register(registry);
        this.errors = Counter.builder("veryal.gateway.protocol.errors").register(registry);
    }

    @PostConstruct
    void registerGauges() {
        registry.gauge(MetricNames.CONNECTIONS_ACTIVE, activeConnections);
    }

    public void connectionOpened() {
        activeConnections.incrementAndGet();
        connectionsCreated.increment();
    }

    public void connectionClosed() {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
        connectionsClosed.increment();
    }

    public void heartbeatTimeout() {
        heartbeatTimeouts.increment();
    }

    public void messageReceived(Command command) {
        registry.counter(MetricNames.MESSAGES_RECEIVED, "command", command.name()).increment();
    }

    public void protocolError(String reason) {
        registry.counter("veryal.gateway.protocol.errors", "reason", reason).increment();
        errors.increment();
    }
}
