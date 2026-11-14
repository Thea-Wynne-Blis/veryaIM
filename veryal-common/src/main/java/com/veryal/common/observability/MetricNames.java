package com.veryal.common.observability;

/**
 * Prometheus 指标名常量。统一命名，避免指标名漂移。
 */
public final class MetricNames {

    public static final String CONNECTIONS_ACTIVE = "veryal.gateway.connections.active";
    public static final String CONNECTIONS_CREATED = "veryal.gateway.connections.created";
    public static final String CONNECTIONS_CLOSED = "veryal.gateway.connections.closed";
    public static final String BYTES_RECEIVED = "veryal.gateway.bytes.received";
    public static final String BYTES_SENT = "veryal.gateway.bytes.sent";
    public static final String HEARTBEAT_TIMEOUT = "veryal.gateway.heartbeat.timeout";

    public static final String MESSAGES_RECEIVED = "veryal.message.received";
    public static final String MESSAGES_PERSISTED = "veryal.message.persisted";
    public static final String MESSAGES_DELIVERED = "veryal.message.delivered";
    public static final String MESSAGE_LATENCY = "veryal.message.latency";
    public static final String DELIVERY_FAILURE = "veryal.message.delivery.failure";

    private MetricNames() {
    }
}
