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
import com.veryal.domain.conversation.ConversationType;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.Message;
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
    private final GroupRepository groupRepository;
    private final OutboxAppender outboxAppender;
    private final ReadReceiptNotifier readReceiptNotifier;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    /**
     * 创建（或获取已存在的）单聊会话。同一对用户永远只有一个单聊会话。
     */
    @Transactional
    public ConversationView createPrivate(long userId, long peerUserId) {
        if (userId == peerUserId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot create private conversation with self");
        }
        User peer = userRepository.findById(peerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "peer user not found"));
        peer.ensureActive();
        Instant now = timeProvider.now();
        String conversationId = ConversationIds.privateId(userId, peerUserId);
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null) {
            Conversation created = Conversation.createPrivate(userId, peerUserId, now);
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
    public List<ConversationSummary> list(long userId) {
        return conversationRepository.findByUser(userId).stream()
                .map(conversation -> {
                    ConversationMember member = conversationRepository.findMember(conversation.id(), userId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
                    return new ConversationSummary(conversation.id(), conversation.type(), member.lastReadSeq(),
                            messageRepository.maxSeq(conversation.id()), conversation.updatedAt());
                })
                .toList();
    }

    /**
     * 会话卡片列表：一次返回标题、成员数、最后一条消息与未读数，供客户端直接渲染。
     */
    @Transactional(readOnly = true)
    public List<ConversationCard> listCards(long userId) {
        return conversationRepository.findByUser(userId).stream()
                .map(conversation -> card(conversation, userId))
                .sorted((a, b) -> {
                    var at = a.lastMessageAt() == null ? a.updatedAt() : a.lastMessageAt();
                    var bt = b.lastMessageAt() == null ? b.updatedAt() : b.lastMessageAt();
                    return bt.compareTo(at);
                })
                .toList();
    }

    private ConversationCard card(Conversation conversation, long userId) {
        String conversationId = conversation.id();
        List<ConversationMember> members = conversationRepository.findMembers(conversationId);
        ConversationMember me = conversationRepository.findMember(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));

        String title = "会话";
        long peerUserId = 0L;
        if (conversation.type() == ConversationType.GROUP) {
            title = groupRepository.findById(conversationId).map(Group::name).orElse("群聊");
        } else {
            for (ConversationMember member : members) {
                if (member.userId() != userId) {
                    peerUserId = member.userId();
                    title = userRepository.findById(peerUserId)
                            .map(User::nickname)
                            .orElse("用户 " + peerUserId);
                    break;
                }
            }
        }

        long lastSeq = messageRepository.maxSeq(conversationId);
        String lastType = null;
        String lastPreview = "";
        Instant lastAt = null;
        var latest = messageRepository.findLatest(conversationId);
        if (latest.isPresent()) {
            var message = latest.get();
            lastType = message.type().name();
            lastPreview = preview(message);
            lastAt = message.createdAt();
        }

        return new ConversationCard(conversationId, conversation.type(), title, peerUserId,
                members.size(), me.lastReadSeq(), lastSeq,
                Math.max(0, lastSeq - me.lastReadSeq()), lastType, lastPreview, lastAt,
                conversation.updatedAt());
    }

    private String preview(Message message) {
        if (message.recalledAt() != null) {
            return "[消息已撤回]";
        }
        String text = switch (message.type()) {
            case TEXT -> new String(message.content(), java.nio.charset.StandardCharsets.UTF_8);
            case EMOJI -> "[表情]";
            case IMAGE -> "[图片]";
            case AUDIO -> "[语音]";
            case VIDEO -> "[视频]";
            case FILE -> "[文件]";
            case SYSTEM -> "[系统消息]";
        };
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 60 ? text.substring(0, 60) + "…" : text;
    }

    /**
     * 校验会话存在、租户一致且用户是成员。
     */
    @Transactional(readOnly = true)
    public ConversationMember requireMember(String conversationId, long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND,
                        "conversation not found: " + conversationId));
        return conversationRepository.findMember(conversation.id(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
    }

    @Transactional(readOnly = true)
    public ConversationView view(String conversationId, long userId) {
        requireMember(conversationId, userId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        return view(conversation);
    }

    /**
     * 推进读游标。游标只前进不后退；真正推进时写入已读回执事件（Outbox）。
     */
    @Transactional
    public void markRead(long userId, String conversationId, long readSequence) {
        markRead(userId, null, conversationId, readSequence);
    }

    @Transactional
    public void markRead(long userId, String deviceId, String conversationId,
                         long readSequence) {
        ConversationMember member = requireMember(conversationId, userId);
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
        ReadReceiptEvent event = new ReadReceiptEvent(RandomIds.newId(), conversationId,
                userId, deviceId, updated.lastReadSeq(), now, memberIds);
        outboxAppender.append(new OutboxMessage(event.eventId(), conversationId,
                MessageEvents.READ_RECEIPT, JsonUtils.toJsonBytes(event), now));
        readReceiptNotifier.notify(event);
    }

    @Transactional(readOnly = true)
    public List<ConversationMember> readReceipts(long userId, String conversationId) {
        requireMember(conversationId, userId);
        return conversationRepository.findMembers(conversationId);
    }

    /**
     * 推进同步游标（Delivery ACK 到达后调用）。游标只前进不后退。
     */
    @Transactional
    public void advanceSyncCursor(long userId, String conversationId, long syncSequence) {
        ConversationMember member = requireMember(conversationId, userId);
        if (syncSequence < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "sync sequence must not be negative");
        }
        conversationRepository.updateMemberCursor(member.advanceSync(syncSequence));
    }

    @Transactional(readOnly = true)
    public List<Long> recipientIds(String conversationId, long senderId) {
        requireMember(conversationId, senderId);
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
