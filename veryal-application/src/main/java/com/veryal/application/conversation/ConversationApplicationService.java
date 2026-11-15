package com.veryal.application.conversation;

import com.veryal.common.core.RandomIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.conversation.Conversation;
import com.veryal.domain.conversation.ConversationIds;
import com.veryal.domain.conversation.ConversationMember;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.event.MessageEvents;
import com.veryal.domain.message.event.ReadReceiptEvent;
import com.veryal.domain.message.event.ReadReceiptNotifier;
import com.veryal.domain.message.outbox.OutboxAppender;
import com.veryal.domain.message.outbox.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 会话应用服务：单聊创建、会话列表、成员校验、读游标推进。
 */
@Service
@RequiredArgsConstructor
public class ConversationApplicationService {

    private static final int MAX_MEMBERS_SHOWN = 500;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OutboxAppender outboxAppender;
    private final ReadReceiptNotifier readReceiptNotifier;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    /**
     * 创建（或获取已存在的）单聊会话。同一对用户永远只有一个单聊会话。
     */
    @Transactional
    public ConversationView createPrivate(long tenantId, long userId, long peerUserId) {
        if (userId == peerUserId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot create private conversation with self");
        }
        userRepository.findById(peerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "peer user not found"));
        Instant now = timeProvider.now();
        String conversationId = ConversationIds.privateId(tenantId, userId, peerUserId);
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null) {
            Conversation created = Conversation.createPrivate(tenantId, userId, peerUserId, now);
            if (conversationRepository.saveIfAbsent(created)) {
                conversation = created;
            } else {
                conversation = conversationRepository.findById(conversationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PERSISTENCE_ERROR,
                                "failed to create conversation"));
            }
        }
        addMemberIfAbsent(conversationId, userId, now);
        addMemberIfAbsent(conversationId, peerUserId, now);
        return view(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(long tenantId, long userId) {
        return conversationRepository.findByUser(tenantId, userId).stream()
                .map(conversation -> {
                    ConversationMember member = conversationRepository.findMember(conversation.id(), userId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
                    return new ConversationSummary(conversation.id(), conversation.type(), member.lastReadSeq(),
                            messageRepository.maxSeq(tenantId, conversation.id()), conversation.updatedAt());
                })
                .toList();
    }

    /**
     * 校验会话存在、租户一致且用户是成员。
     */
    @Transactional(readOnly = true)
    public ConversationMember requireMember(long tenantId, String conversationId, long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(item -> item.tenantId() == tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND,
                        "conversation not found: " + conversationId));
        return conversationRepository.findMember(conversation.id(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
    }

    @Transactional(readOnly = true)
    public ConversationView view(String conversationId, long tenantId, long userId) {
        requireMember(tenantId, conversationId, userId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        return view(conversation);
    }

    /**
     * 推进读游标。游标只前进不后退；真正推进时写入已读回执事件（Outbox）。
     */
    @Transactional
    public void markRead(long tenantId, long userId, String conversationId, long readSequence) {
        markRead(tenantId, userId, null, conversationId, readSequence);
    }

    @Transactional
    public void markRead(long tenantId, long userId, String deviceId, String conversationId,
                         long readSequence) {
        ConversationMember member = requireMember(tenantId, conversationId, userId);
        if (readSequence < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "read sequence must not be negative");
        }
        ConversationMember updated = member.advanceRead(readSequence);
        if (updated.lastReadSeq() == member.lastReadSeq()) {
            return;
        }
        conversationRepository.updateMemberCursor(updated);
        Instant now = timeProvider.now();
        List<Long> memberIds = conversationRepository.findMembers(conversationId).stream()
                .map(ConversationMember::userId)
                .toList();
        ReadReceiptEvent event = new ReadReceiptEvent(RandomIds.newId(), tenantId, conversationId,
                userId, deviceId, updated.lastReadSeq(), now, memberIds);
        outboxAppender.append(new OutboxMessage(event.eventId(), conversationId,
                MessageEvents.READ_RECEIPT, JsonUtils.toJsonBytes(event), now));
        readReceiptNotifier.notify(event);
    }

    @Transactional(readOnly = true)
    public List<ConversationMember> readReceipts(long tenantId, long userId, String conversationId) {
        requireMember(tenantId, conversationId, userId);
        return conversationRepository.findMembers(conversationId);
    }

    /**
     * 推进同步游标（Delivery ACK 到达后调用）。游标只前进不后退。
     */
    @Transactional
    public void advanceSyncCursor(long tenantId, long userId, String conversationId, long syncSequence) {
        ConversationMember member = requireMember(tenantId, conversationId, userId);
        if (syncSequence < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "sync sequence must not be negative");
        }
        conversationRepository.updateMemberCursor(member.advanceSync(syncSequence));
    }

    @Transactional(readOnly = true)
    public List<Long> recipientIds(long tenantId, String conversationId, long senderId) {
        requireMember(tenantId, conversationId, senderId);
        return conversationRepository.findMembers(conversationId).stream()
                .map(ConversationMember::userId)
                .filter(userId -> userId != senderId)
                .limit(MAX_MEMBERS_SHOWN)
                .toList();
    }

    private void addMemberIfAbsent(String conversationId, long userId, Instant now) {
        if (conversationRepository.findMember(conversationId, userId).isEmpty()) {
            conversationRepository.addMember(ConversationMember.join(idGenerator.nextId(), conversationId, userId, now));
        }
    }

    private ConversationView view(Conversation conversation) {
        List<Long> memberIds = conversationRepository.findMembers(conversation.id()).stream()
                .map(ConversationMember::userId)
                .toList();
        return new ConversationView(conversation.id(), conversation.type(), memberIds,
                conversation.createdAt(), conversation.updatedAt());
    }
}
