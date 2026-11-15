package com.veryal.infrastructure.mq;

/**
 * RabbitMQ 拓扑常量。
 *
 * <p>事件类型同时作为 routing key；队列与交换机名称一经上线不可随意修改。</p>
 */
public final class RabbitConstants {

    public static final String MESSAGE_EXCHANGE = "veryal.message.exchange";
    public static final String RETRY_EXCHANGE = "veryal.retry.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "veryal.dlx.exchange";
    public static final String GATEWAY_EXCHANGE = "veryal.gateway.exchange";

    public static final String DELIVERY_QUEUE = "veryal-message-delivery-queue";
    public static final String RETRY_QUEUE = "veryal-message-delivery-retry-queue";
    public static final String DEAD_LETTER_QUEUE = "veryal-dead-letter-queue";
    public static final String GATEWAY_PUSH_QUEUE = "veryal-gateway-push-queue";
    public static final String READ_RECEIPT_QUEUE = "veryal-read-receipt-queue";

    public static final String RETRY_ROUTING_KEY = "message.delivery.retry";
    public static final String DEAD_ROUTING_KEY = "message.delivery.dead";
    public static final String GATEWAY_PUSH_ROUTING_PREFIX = "gateway.push.";

    public static final String HEADER_RETRY_COUNT = "x-retry-count";

    public static final int RETRY_TTL_MILLIS = 5_000;

    private RabbitConstants() {
    }
}
