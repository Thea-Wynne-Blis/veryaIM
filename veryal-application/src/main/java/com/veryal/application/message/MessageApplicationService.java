package com.veryal.application.message;

import com.veryal.common.core.CursorPage;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.conversation.Conversation;
import com.veryal.domain.conversation.ConversationMember;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.message.ConversationSequenceRepository;
import com.veryal.domain.message.DuplicateMessageException;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessagePolicy;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 消息应用服务。
 *
 * <p>发送流程：成员校验 → 幂等检查 → 分配会话序号 → 持久化消息 → 返回 ACK。
 * 消息持久化是可靠性的唯一依据，实时投递失败不影响消息本身。</p>
 */
@Service
public class MessageApplicationService {

    private static final int MAX_HISTORY_LIMIT = 200;

    private final MessageRepository messageRepository;
    private final ConversationSequenceRepository sequenceRepository;
    private final ConversationRepository conversationRepository;
    private final DeliveryTracker deliveryTracker;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public MessageApplicationService(MessageRepository messageRepository,
                                     ConversationSequenceRepository sequenceRepository,
                                     ConversationRepository conversationRepository,
                                     DeliveryTracker deliveryTracker,
                                     IdGenerator idGenerator,
                                     TimeProvider timeProvider) {
        this.messageRepository = messageRepository;
        this.sequenceRepository = sequenceRepository;
        this.conversationRepository = conversationRepository;
        this.deliveryTracker = deliveryTracker;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

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
            return new SendMessageResult(existing.get(), recipients(conversation.id(), command.senderId()), true);
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
            return new SendMessageResult(duplicate, recipients(conversation.id(), command.senderId()), true);
        }
        conversationRepository.touch(conversation.id(), now);
        return new SendMessageResult(message, recipients(conversation.id(), command.senderId()), false);
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

    private List<Long> recipients(String conversationId, long senderId) {
        return conversationRepository.findMembers(conversationId).stream()
                .map(ConversationMember::userId)
                .filter(userId -> userId != senderId)
                .toList();
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }
}
