package com.veryal.access.core.push.local;

import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.access.core.push.RealtimeFanoutExecutor;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.domain.message.event.MessageEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本节点实时投递实现（单机直投模式）。
 *
 * <p>私聊使用事件中的接收者快照；群聊事件不携带接收者，按会话成员分页拉取后推送。</p>
 *
 * <p>与 MQ 出箱路由共用 {@link MessageFrameWriter}，两条链路命中同一连接时只发一次。</p>
 *
 * <p>扇出交给 {@link RealtimeFanoutExecutor}（有界队列、丢最旧），发送请求线程不再等待
 * 逐个连接写入；不可写（慢连接）直接跳过，客户端靠轮询/游标补齐。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeDelivery implements RealtimeDeliveryPort {

    private static final int MEMBER_PAGE_SIZE = 500;

    private final ConnectionRegistry connectionRegistry;
    private final ConversationRepository conversationRepository;
    private final MessageFrameWriter frameWriter;
    private final RealtimeFanoutExecutor fanoutExecutor;

    @Override
    public void deliver(MessageCreatedEvent event) {
        // 落库已提交：扇出放到有界线程池，发送接口不再等待本节点逐个连接写入。
        fanoutExecutor.submit(() -> doDeliver(event));
    }

    private void doDeliver(MessageCreatedEvent event) {
        MessageFrame frame = MessageFrame.of(event);
        Set<String> pushed = new HashSet<>();

        if (MessageEvents.GROUP_CREATED.equals(event.eventType())) {
            long cursor = 0;
            while (true) {
                List<Long> page = conversationRepository.findMemberIdsPage(event.conversationId(),
                        cursor, MEMBER_PAGE_SIZE);
                if (page.isEmpty()) {
                    break;
                }
                pushUsers(page, frame, event, pushed);
                cursor = page.get(page.size() - 1);
                if (page.size() < MEMBER_PAGE_SIZE) {
                    break;
                }
            }
            return;
        }

        Set<Long> users = new LinkedHashSet<>(event.recipientUserIds());
        users.add(event.senderId());
        pushUsers(users, frame, event, pushed);
    }

    private void pushUsers(Collection<Long> userIds, MessageFrame frame, MessageCreatedEvent event,
                           Set<String> pushed) {
        for (Long userId : userIds) {
            pushToUser(userId, frame, event, pushed);
        }
    }

    private void pushToUser(long userId, MessageFrame frame, MessageCreatedEvent event,
                            Set<String> pushed) {
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
            if (frameWriter.write(frame, connection)) {
                log.debug("message pushed: userId={}, connectionId={}, messageId={}",
                        userId, connection.connectionId(), event.messageId());
            }
        }
    }
}
