package com.veryal.domain.message.event;

/**
 * 消息领域事件类型与 MQ Routing Key。
 *
 * <p>事件类型同时作为 Outbox 的 event_type 与 RabbitMQ routing key，禁止随意改名。</p>
 */
public final class MessageEvents {

    public static final String PRIVATE_CREATED = "message.private.created";

    public static final String GROUP_CREATED = "message.group.created";

    public static final String READ_RECEIPT = "message.read";

    public static final String MESSAGE_RECALLED = "message.recalled";

    private MessageEvents() {
    }
}
