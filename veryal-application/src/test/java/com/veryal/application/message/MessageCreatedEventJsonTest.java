package com.veryal.application.message;

import com.veryal.common.json.JsonUtils;
import com.veryal.domain.message.event.MessageCreatedEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageCreatedEventJsonTest {

    @Test
    void roundTripsEventThroughJsonForOutboxAndConsumer() {
        MessageCreatedEvent event = new MessageCreatedEvent("event-1", 1L, "p_1_1001_1002",
                9001L, 1001L, "device-a", 7L, 1,
                "hello".getBytes(StandardCharsets.UTF_8), Instant.parse("2026-01-01T00:00:00Z"),
                "client-msg-1", List.of(1002L, 1003L));

        byte[] payload = JsonUtils.toJsonBytes(event);
        MessageCreatedEvent decoded = JsonUtils.fromJson(payload, MessageCreatedEvent.class);

        assertThat(decoded.eventId()).isEqualTo(event.eventId());
        assertThat(decoded.tenantId()).isEqualTo(event.tenantId());
        assertThat(decoded.conversationId()).isEqualTo(event.conversationId());
        assertThat(decoded.messageId()).isEqualTo(event.messageId());
        assertThat(decoded.senderId()).isEqualTo(event.senderId());
        assertThat(decoded.senderDeviceId()).isEqualTo(event.senderDeviceId());
        assertThat(decoded.seq()).isEqualTo(event.seq());
        assertThat(decoded.messageType()).isEqualTo(event.messageType());
        assertThat(decoded.content()).isEqualTo(event.content());
        assertThat(decoded.createdAt()).isEqualTo(event.createdAt());
        assertThat(decoded.clientMessageId()).isEqualTo(event.clientMessageId());
        assertThat(decoded.recipientUserIds()).containsExactly(1002L, 1003L);
    }
}
