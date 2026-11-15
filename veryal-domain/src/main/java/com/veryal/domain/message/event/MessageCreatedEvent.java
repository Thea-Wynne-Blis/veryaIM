package com.veryal.domain.message.event;

import java.time.Instant;
import java.util.List;

/**
 * 消息已持久化领域事件。
 *
 * <p>由应用层在消息事务内写入 Outbox，再由 MQ 消费者驱动实时投递。
 * 事件包含接收者快照，单聊/群聊都无需消费者回查成员关系。</p>
 */
public record MessageCreatedEvent(String eventId, String eventType, String conversationId,
                                  long messageId, long senderId, String senderDeviceId,
                                  long seq, int messageType, byte[] content, Instant createdAt,
                                  String clientMessageId, List<Long> recipientUserIds) {

    public MessageCreatedEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (messageId <= 0 || senderId <= 0 || seq <= 0) {
            throw new IllegalArgumentException("messageId, senderId and seq must be positive");
        }
        if (content == null) {
            content = new byte[0];
        }
        if (recipientUserIds == null) {
            recipientUserIds = List.of();
        }
        recipientUserIds = List.copyOf(recipientUserIds);
    }
}
