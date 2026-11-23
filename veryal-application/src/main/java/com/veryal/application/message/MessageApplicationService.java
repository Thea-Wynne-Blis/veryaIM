package com.veryal.application.message;

import com.veryal.application.audit.AuditRecorder;
import com.veryal.application.group.GroupModerationService;
import com.veryal.application.message.strategy.MessageContentContext;
import com.veryal.application.message.strategy.MessageTypeStrategyFactory;
import com.veryal.application.moderation.SensitiveWordService;
import com.veryal.common.core.CursorPage;
import com.veryal.common.core.RandomIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.audit.AuditAction;
import com.veryal.domain.conversation.Conversation;
import com.veryal.domain.conversation.ConversationMember;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.conversation.ConversationType;
import com.veryal.domain.conversation.SendContext;
import com.veryal.domain.delivery.DeliveryRecord;
import com.veryal.domain.delivery.DeliveryRepository;
import com.veryal.domain.message.ConversationSequenceRepository;
import com.veryal.domain.message.DuplicateMessageException;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessagePolicy;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.domain.message.event.MessageEvents;
import com.veryal.domain.message.event.MessageRecalledEvent;
import com.veryal.domain.message.event.MessageRecallNotifier;
import com.veryal.domain.message.outbox.OutboxAppender;
import com.veryal.domain.message.outbox.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final java.time.Duration RECALL_WINDOW = java.time.Duration.ofMinutes(2);

    private final MessageRepository messageRepository;
    private final ConversationSequenceRepository sequenceRepository;
    private final ConversationRepository conversationRepository;
    private final OutboxAppender outboxAppender;
    private final DeliveryRepository deliveryRepository;
    /** 同一会话 2 秒内只更新一次活跃时间，避免每条消息都去锁同一行。 */
    private final Map<String, Long> recentTouches = new ConcurrentHashMap<>();
    private static final long TOUCH_MIN_INTERVAL_MS = 2_000L;
    private static final int TOUCH_CACHE_LIMIT = 20_000;
    private final MessageRateLimiter messageRateLimiter;
    private final MessageTypeStrategyFactory messageTypeStrategyFactory;
    private final AuditRecorder auditRecorder;
    private final GroupModerationService groupModerationService;
    private final SensitiveWordService sensitiveWordService;
    private final MessageRecallNotifier messageRecallNotifier;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public SendMessageResult send(SendMessageCommand command) {
        MessagePolicy.validateConversationId(command.conversationId());
        MessagePolicy.validateClientMessageId(command.clientMessageId());
        MessagePolicy.validate(command.messageType(), command.content());
        messageTypeStrategyFactory.validate(new MessageContentContext(
                command.senderId(), command.conversationId(), command.messageType(), command.content()));

        // 一次查询取回会话类型、成员关系、禁言与私聊拉黑状态：此前每条消息要为此多付 5 次数据库往返。
        SendContext context = conversationRepository
                .findSendContext(command.conversationId(), command.senderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND,
                        "conversation not found: " + command.conversationId()));
        if (!context.member()) {
            throw new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
        if (context.type() == ConversationType.GROUP) {
            groupModerationService.ensureCanSend(command.conversationId(), command.senderId());
            groupModerationService.validateMentions(command.conversationId(), command.content());
        }
        if (context.peerBlocked()) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }

        Optional<Message> existing = messageRepository.findByIdempotencyKey(
                command.senderId(), command.clientMessageId());
        if (existing.isPresent()) {
            return new SendMessageResult(existing.get(), null, true);
        }

        // 引用：只能引用同一会话里未被撤回的消息，快照原文，原消息后续被撤回也不影响阅读。
        Message replyTarget = null;
        if (command.replyToId() != null) {
            replyTarget = messageRepository.findById(command.replyToId())
                    .filter(item -> item.conversationId().equals(command.conversationId()))
                    .filter(item -> !item.recalled())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND,
                            "reply target not found in this conversation"));
        }

        // 幂等命中不消耗限流额度；只有真正的新消息才检查发送速率。
        sensitiveWordService.checkMessage(command.messageType(), command.content());
        Instant now = timeProvider.now();
        if (context.mutedUntil() != null && context.mutedUntil().isAfter(now)) {
            throw new BusinessException(ErrorCode.USER_MUTED);
        }
        messageRateLimiter.acquire(command.senderId());
        long sequence = sequenceRepository.allocate(command.conversationId());
        Message message = Message.create(idGenerator.nextId(), command.conversationId(),
                command.senderId(), command.clientMessageId(), sequence, command.messageType(),
                command.content(), now,
                replyTarget == null ? null : replyTarget.id(),
                replyTarget == null ? null : replyTarget.senderId(),
                replyTarget == null ? null : replyPreviewOf(replyTarget));
        try {
            messageRepository.append(message);
        } catch (DuplicateMessageException e) {
            Message duplicate = messageRepository.findByIdempotencyKey(
                            command.senderId(), command.clientMessageId())
                    .orElseThrow(() -> e);
            return new SendMessageResult(duplicate, null, true);
        }

        MessageCreatedEvent event = createEvent(command, message, context, now);
        outboxAppender.append(new OutboxMessage(event.eventId(), command.conversationId(),
                event.eventType(), JsonUtils.toJsonBytes(event), now));
        // 发送即视为已读：否则自己刚发的消息会让会话列表显示未读。
        // 单条 UPDATE + GREATEST，不需要先读后写；这里不发已读回执事件，避免每条消息多一次 Outbox。
        conversationRepository.advanceReadCursor(command.conversationId(), command.senderId(), sequence);
        touchThrottled(command.conversationId(), now);
        return new SendMessageResult(message, event, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<Message> history(long userId, String conversationId,
                                       long cursor, int limit) {
        requireMember(conversationId, userId);
        int pageSize = clampLimit(limit);
        // 清空记录是「本人可见水位」：cursor 之外再从水位之后开始翻，别把删掉的记录翻回来。
        long afterSeq = Math.max(cursor, clearedSeqOf(conversationId, userId));
        return CursorPage.slice(messageRepository.pageAfter(conversationId, afterSeq, pageSize + 1),
                pageSize, afterSeq, Message::seq);
    }

    /** 搜索结果定位：校验可见水位后返回目标消息前后的上下文。 */
    @Transactional(readOnly = true)
    public CursorPage<Message> context(long userId, String conversationId, long messageId) {
        requireMember(conversationId, userId);
        long clearedSeq = clearedSeqOf(conversationId, userId);
        Message target = messageRepository.findById(messageId)
                .filter(message -> message.conversationId().equals(conversationId))
                .filter(message -> message.seq() > clearedSeq && !message.recalled())
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        return history(userId, conversationId, Math.max(clearedSeq, target.seq() - 16), 40);
    }

    /**
     * 会话内文本/表情搜索，按 seq 倒序返回；cursor 为上一页最后一条 seq。
     */
    @Transactional(readOnly = true)
    public CursorPage<Message> search(long userId, String conversationId,
                                      String keyword, long cursor, int limit) {
        requireMember(conversationId, userId);
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty() || query.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "search keyword must be 1-64 characters");
        }
        int pageSize = clampLimit(limit);
        String escaped = query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String pattern = "%" + escaped + "%";
        long beforeSeq = cursor <= 0 ? Long.MAX_VALUE : cursor;
        return CursorPage.slice(messageRepository.searchText(conversationId, pattern,
                        clearedSeqOf(conversationId, userId), beforeSeq, pageSize + 1),
                pageSize, cursor, Message::seq);
    }

    /**
     * 处理 Delivery ACK：持久化设备投递记录，并返回消息所属会话。
     *
     * @return 消息所属会话 ID
     */
    public String acknowledgeDelivery(long userId, long messageId, String deviceId,
                                      long deliveredSequence) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        requireMember(message.conversationId(), userId);
        String normalizedDeviceId = deviceId == null || deviceId.isBlank()
                ? Long.toString(userId) : deviceId;
        deliveryRepository.record(new DeliveryRecord(idGenerator.nextId(), message.id(),
                message.conversationId(), userId, normalizedDeviceId, deliveredSequence,
                timeProvider.now()));
        return message.conversationId();
    }

    /**
     * 撤回消息：仅发送者本人，且必须在发送后 2 分钟内；重复撤回保持幂等。
     */
    @Transactional
    public Message recall(long userId, String conversationId, long messageId) {
        return recall(userId, conversationId, messageId, null);
    }

    @Transactional
    public Message recall(long userId, String conversationId, long messageId,
                          String senderDeviceId) {
        requireMember(conversationId, userId);
        Message message = messageRepository.findById(messageId)
                .filter(item -> item.conversationId().equals(conversationId))
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (message.senderId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only sender can recall message");
        }
        if (message.recalled()) {
            return message;
        }
        Instant now = timeProvider.now();
        if (now.isAfter(message.createdAt().plus(RECALL_WINDOW))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "recall window expired");
        }
        messageRepository.markRecalled(messageId, userId, now);
        auditRecorder.record(userId, AuditAction.MESSAGE_RECALL, "message",
                Long.toString(messageId), "conversation=" + conversationId);
        List<Long> memberIds = conversationRepository.findMembers(conversationId).stream()
                .map(ConversationMember::userId)
                .toList();
        MessageRecalledEvent event = new MessageRecalledEvent(RandomIds.newId(),
                conversationId, messageId, userId, senderDeviceId, message.seq(), now, memberIds);
        outboxAppender.append(new OutboxMessage(event.eventId(), conversationId,
                MessageEvents.MESSAGE_RECALLED, JsonUtils.toJsonBytes(event), now));
        messageRecallNotifier.notify(event);
        return message.recall(userId, now);
    }

    /**
     * 查询单条消息的设备投递记录（仅会话成员可见）。
     */
    @Transactional(readOnly = true)
    public List<DeliveryRecord> listDeliveries(long userId, String conversationId,
                                               long messageId) {
        requireMember(conversationId, userId);
        Message message = messageRepository.findById(messageId)
                .filter(item -> item.conversationId().equals(conversationId))
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        return deliveryRepository.findByMessage(message.id());
    }

    @Transactional(readOnly = true)
    public void requireMember(String conversationId, long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        conversationRepository.findMember(conversation.id(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
    }

    /** 本人「清空记录」的水位；成员关系已由 {@code requireMember} 校验过。 */
    private long clearedSeqOf(String conversationId, long userId) {
        return conversationRepository.findMemberPrefs(conversationId, userId)
                .map(prefs -> prefs.clearedSeq())
                .orElse(0L);
    }

    /** 同一会话 2 秒内只刷新一次活跃时间：避免每条消息都更新同一行导致发送被串行化。 */
    private void touchThrottled(String conversationId, Instant now) {
        long nowMillis = System.currentTimeMillis();
        Long previous = recentTouches.get(conversationId);
        if (previous != null && nowMillis - previous < TOUCH_MIN_INTERVAL_MS) {
            return;
        }
        recentTouches.put(conversationId, nowMillis);
        if (recentTouches.size() > TOUCH_CACHE_LIMIT) {
            recentTouches.clear();
        }
        conversationRepository.touch(conversationId, now);
    }

    /** 引用快照在客户端只占一行，超长直接截断，避免消息表存整段历史。 */
    private static final int REPLY_PREVIEW_MAX = 120;

    private static String replyPreviewOf(Message target) {
        String preview = switch (target.type()) {
            case TEXT -> target.textContent();
            case EMOJI -> target.textContent().strip().startsWith("{")
                    ? "[表情]" : target.textContent();
            case IMAGE -> "[图片]";
            case VIDEO -> "[视频]";
            case AUDIO -> "[语音]";
            case FILE -> "[文件]";
            case SYSTEM -> "[系统消息]";
        };
        String flat = preview.replaceAll("\\s+", " ").strip();
        if (flat.isEmpty()) {
            return "消息";
        }
        return flat.length() <= REPLY_PREVIEW_MAX
                ? flat : flat.substring(0, REPLY_PREVIEW_MAX) + "…";
    }

    private MessageCreatedEvent createEvent(SendMessageCommand command, Message message,
                                            SendContext context, Instant now) {
        String eventType = context.type() == ConversationType.GROUP
                ? MessageEvents.GROUP_CREATED
                : MessageEvents.PRIVATE_CREATED;
        // 群消息不携带接收者快照，由消费者/本地投递按成员分页拉取，避免大群事件体积膨胀。
        // 私聊接收者就是发送上下文里已经查到的对端，不用再查一次成员表。
        List<Long> recipients = context.type() == ConversationType.GROUP || context.peerUserId() == null
                ? List.of()
                : List.of(context.peerUserId());
        return new MessageCreatedEvent(RandomIds.newId(), eventType, message.conversationId(),
                message.id(), message.senderId(), command.senderDeviceId(),
                message.seq(), message.type().code(), message.content(), now,
                message.clientMsgId(), recipients,
                message.replyToId(), message.replySenderId(), message.replyPreview());
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }
}
