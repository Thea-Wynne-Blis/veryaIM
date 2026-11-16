package com.veryal.access.core.push;

import com.google.protobuf.ByteString;
import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import com.veryal.domain.delivery.RealtimeStreamPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.domain.message.event.MessageEvents;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.MessageNotify;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本节点实时投递实现（单机直投模式）。
 *
 * <p>私聊使用事件中的接收者快照；群聊事件不携带接收者，按会话成员分页拉取后推送。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeDelivery implements RealtimeDeliveryPort {

    private static final int MEMBER_PAGE_SIZE = 500;
    private static final long DEDUPE_TTL_MS = 60_000L;
    private static final int DEDUPE_MAX_ENTRIES = 50_000;

    private final ConnectionRegistry connectionRegistry;
    private final ConversationRepository conversationRepository;
    private final RealtimeStreamPort realtimeStreamPort;
    private final Map<String, Long> deliveredEvents = new ConcurrentHashMap<>();

    @Override
    public void deliver(MessageCreatedEvent event) {
        // 单机直推与 MQ 消费者会先后到达同一事件，这里按 eventId 去重，
        // 保证 MQ 开启时既保留本地零延迟，又不会重复推送。
        long nowMillis = System.currentTimeMillis();
        Long previous = deliveredEvents.putIfAbsent(event.eventId(), nowMillis);
        if (previous != null) {
            return;
        }
        if (deliveredEvents.size() > DEDUPE_MAX_ENTRIES) {
            purgeExpired(nowMillis);
        }

        MessageNotify notify = MessageNotify.newBuilder()
                .setMessageId(event.messageId())
                .setConversationId(event.conversationId())
                .setSenderId(event.senderId())
                .setSequence(event.seq())
                .setMessageType(event.messageType())
                .setContent(ByteString.copyFrom(event.content()))
                .setCreatedAt(event.createdAt().toEpochMilli())
                .setClientMessageId(event.clientMessageId() == null ? "" : event.clientMessageId())
                .build();
        byte[] payload = notify.toByteArray();
        Set<String> pushed = new HashSet<>();

        if (MessageEvents.GROUP_CREATED.equals(event.eventType())) {
            long cursor = 0;
            while (true) {
                List<Long> page = conversationRepository.findMemberIdsPage(event.conversationId(),
                        cursor, MEMBER_PAGE_SIZE);
                if (page.isEmpty()) {
                    break;
                }
                pushUsers(page, event, payload, pushed);
                cursor = page.get(page.size() - 1);
                if (page.size() < MEMBER_PAGE_SIZE) {
                    break;
                }
            }
            realtimeStreamPort.publish(event);
            return;
        }

        Set<Long> users = new LinkedHashSet<>(event.recipientUserIds());
        users.add(event.senderId());
        pushUsers(users, event, payload, pushed);
        realtimeStreamPort.publish(event);
    }

    private void pushUsers(Collection<Long> userIds, MessageCreatedEvent event, byte[] payload,
                           Set<String> pushed) {
        for (Long userId : userIds) {
            pushToUser(userId, event, payload, pushed);
        }
    }

    private void pushToUser(long userId, MessageCreatedEvent event, byte[] payload, Set<String> pushed) {
        for (Connection connection : connectionRegistry.findByUserId(userId)) {
            if (!connection.authenticated()) {
                continue;
            }
            if (userId == event.senderId() && event.senderDeviceId() != null
                    && event.senderDeviceId().equals(connection.deviceId())) {
                continue;
            }
            if (!pushed.add(connection.connectionId())) {
                continue;
            }
            connection.send(ProtocolMessage.of(Command.MESSAGE_NOTIFY, connection.nextSequence(),
                    null, null, payload));
            log.debug("message pushed: userId={}, connectionId={}, messageId={}",
                    userId, connection.connectionId(), event.messageId());
        }
    }

    /** 定期清理去重表，避免长期运行后无限增长。 */
    private void purgeExpired(long nowMillis) {
        long threshold = nowMillis - DEDUPE_TTL_MS;
        deliveredEvents.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }
}
