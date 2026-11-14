package com.veryal.application.message;

import com.veryal.common.core.CursorPage;
import com.veryal.common.core.RandomIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.conversation.Conversation;
import com.veryal.domain.conversation.ConversationMember;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.conversation.ConversationType;
import com.veryal.domain.message.ConversationSequenceRepository;
import com.veryal.domain.message.DuplicateMessageException;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessagePolicy;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.domain.message.event.MessageEvents;
import com.veryal.domain.message.outbox.OutboxAppender;
import com.veryal.domain.message.outbox.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 消息应用服务。
 *
 * <p>发送流程：成员校验 → 幂等检查 → 分配会话序号 → 持久化消息 → 写入 Outbox → 提交事务。
 * 实时投递由 Outbox → RabbitMQ → 消费者完成，投递失败不影响消息可靠性。</p>
 */
@Service
@RequiredArgsConstructor
public class MessageApplicationService {

    private static final int MAX_HISTORY_LIMIT = 200;

    private final MessageRepository messageRepository;
    private final ConversationSequenceRepository sequenceRepository;
    private final ConversationRepository conversationRepository;
    private final OutboxAppender outboxAppender;
    private final DeliveryTracker deliveryTracker;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public SendMessageResult send(SendMessageCommand command) {
        MessagePolicy.validateConversationId(command.conversationId());
        MessagePolicy.validateClientMessageId(command.clientMessageId());
        MessagePolicy.validate(command.messageType(), command.content());

        Conversation conversation = conversationRepository.findById(command.conversationId())
                .filter(item -> item.tenantId() == command.tenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND,
                        "conversation not found: " + command.conversationId()));
        conversationRepository.findMember(conversation.id(), command.senderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));

        Optional<Message> existing = messageRepository.findByIdempotencyKey(
                command.tenantId(), command.senderId(), command.clientMessageId());
        if (existing.isPresent()) {
            return new SendMessageResult(existing.get(), null, true);
        }

        Instant now = timeProvider.now();
        long sequence = sequenceRepository.allocate(command.tenantId(), conversation.id());
        Message message = Message.create(idGenerator.nextId(), command.tenantId(), conversation.id(),
                command.senderId(), command.clientMessageId(), sequence, command.messageType(),
                command.content(), now);
        try {
            messageRepository.append(message);
        } catch (DuplicateMessageException e) {
            Message duplicate = messageRepository.findByIdempotencyKey(
                            command.tenantId(), command.senderId(), command.clientMessageId())
                    .orElseThrow(() -> e);
            return new SendMessageResult(duplicate, null, true);
        }

        MessageCreatedEvent event = createEvent(command, message, conversation, now);
        outboxAppender.append(new OutboxMessage(event.eventId(), conversation.id(),
                event.eventType(), JsonUtils.toJsonBytes(event), now));
        conversationRepository.touch(conversation.id(), now);
        return new SendMessageResult(message, event, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<Message> history(long tenantId, long userId, String conversationId,
                                       long cursor, int limit) {
        requireMember(tenantId, conversationId, userId);
        int pageSize = clampLimit(limit);
        List<Message> messages = messageRepository.pageAfter(tenantId, conversationId, cursor, pageSize + 1);
        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = messages.subList(0, pageSize);
        }
        long nextCursor = messages.isEmpty() ? cursor : messages.get(messages.size() - 1).seq();
        return new CursorPage<>(messages, Long.toString(nextCursor), hasMore);
    }

    /**
     * 处理 Delivery ACK。V0.1 只做内存跟踪与指标，不改变消息持久化状态。
     *
     * @return 消息所属会话 ID
     */
    public String acknowledgeDelivery(long tenantId, long userId, long messageId, String deviceId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        requireMember(tenantId, message.conversationId(), userId);
        deliveryTracker.record(message.id(), deviceId == null || deviceId.isBlank()
                ? Long.toString(userId) : deviceId);
        return message.conversationId();
    }

    @Transactional(readOnly = true)
    public long latestSequence(long tenantId, String conversationId) {
        return messageRepository.maxSeq(tenantId, conversationId);
    }

    @Transactional(readOnly = true)
    public void requireMember(long tenantId, String conversationId, long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(item -> item.tenantId() == tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        conversationRepository.findMember(conversation.id(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
    }

    private MessageCreatedEvent createEvent(SendMessageCommand command, Message message,
                                            Conversation conversation, Instant now) {
        List<Long> recipients = conversationRepository.findMembers(message.conversationId()).stream()
                .map(ConversationMember::userId)
                .filter(userId -> userId != command.senderId())
                .toList();
        String eventType = conversation.type() == ConversationType.GROUP
                ? MessageEvents.GROUP_CREATED
                : MessageEvents.PRIVATE_CREATED;
        return new MessageCreatedEvent(RandomIds.newId(), eventType, message.tenantId(),
                message.conversationId(), message.id(), message.senderId(), command.senderDeviceId(),
                message.seq(), message.type().code(), message.content(), now,
                message.clientMsgId(), recipients);
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }
}
