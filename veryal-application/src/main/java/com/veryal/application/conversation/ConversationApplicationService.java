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
import com.veryal.domain.conversation.ConversationMemberPrefs;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.conversation.ConversationType;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.StoredFile;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.MessageType;
import com.veryal.domain.message.event.MessageEvents;
import com.veryal.domain.message.event.ReadReceiptEvent;
import com.veryal.domain.message.event.ReadReceiptNotifier;
import com.veryal.domain.message.outbox.OutboxAppender;
import com.veryal.domain.message.outbox.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话应用服务：单聊创建、会话列表、成员校验、读游标推进。
 */
@Service
@RequiredArgsConstructor
public class ConversationApplicationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final FileRepository fileRepository;
    private final FriendRepository friendRepository;
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
        // 与 listCards 共用同一条批量查询，避免每会话 3 次往返（成员游标 + max(seq)）。
        return conversationRepository.findCardSnapshots(userId).stream()
                .map(snapshot -> new ConversationSummary(snapshot.conversationId(), snapshot.type(),
                        snapshot.lastReadSeq(), snapshot.lastSeq(), snapshot.updatedAt()))
                .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
                .toList();
    }

    /**
     * 会话卡片列表：一次返回标题、成员数、最后一条消息与未读数，供客户端直接渲染。
     */
    @Transactional(readOnly = true)
    public List<ConversationCard> listCards(long userId) {
        // 一次联表查询取回全部卡片数据：此前的实现是每会话 5 条 SQL（成员列表、本人游标、
        // 对端资料、最大序号、最后一条消息），30 个会话就是 ~150 次往返、73ms 线性成本。
        return conversationRepository.findCardSnapshots(userId).stream()
                .map(snapshot -> {
                    long peerUserId = snapshot.peerUserId() == null ? 0L : snapshot.peerUserId();
                    String title;
                    if (snapshot.type() == ConversationType.GROUP) {
                        title = snapshot.groupName() == null ? "群聊" : snapshot.groupName();
                    } else if (peerUserId > 0) {
                        // 好友备注优先：改名/备注后列表与聊天头部都跟着变。
                        title = snapshot.peerRemark() != null
                                && !snapshot.peerRemark().isBlank()
                                ? snapshot.peerRemark()
                                : snapshot.peerNickname() == null
                                        ? "用户 " + peerUserId
                                        : snapshot.peerNickname();
                    } else {
                        title = "会话";
                    }

                    String lastType = null;
                    String lastPreview = "";
                    long lastSenderId = 0L;
                    String lastSenderName = null;
                    if (snapshot.lastMessageTypeCode() != null) {
                        MessageType type = MessageType.fromCode(snapshot.lastMessageTypeCode());
                        lastType = type.name();
                        lastPreview = preview(type, snapshot.lastMessageContent(),
                                snapshot.lastMessageRecalled());
                        if (snapshot.lastMessageSenderId() != null) {
                            lastSenderId = snapshot.lastMessageSenderId();
                            lastSenderName = snapshot.lastMessageSenderName() == null
                                    || snapshot.lastMessageSenderName().isBlank()
                                    ? "用户 " + lastSenderId
                                    : snapshot.lastMessageSenderName();
                        }
                    }

                    return new ConversationCard(snapshot.conversationId(), snapshot.type(), title,
                            peerUserId, snapshot.memberCount(), snapshot.lastReadSeq(),
                            snapshot.lastSeq(),
                            // 清空记录的水位也参与未读计算；手动未读至少为 1。
                            Math.max(snapshot.manualUnread() ? 1L : 0L,
                                    Math.max(0L, snapshot.lastSeq()
                                            - Math.max(snapshot.lastReadSeq(), snapshot.clearedSeq()))),
                            lastType, lastPreview, snapshot.lastMessageAt(), snapshot.updatedAt(),
                            snapshot.pinned(), snapshot.muted(), snapshot.hidden(),
                            lastSenderId, lastSenderName);
                })
                .sorted((a, b) -> {
                    var at = a.lastMessageAt() == null ? a.updatedAt() : a.lastMessageAt();
                    var bt = b.lastMessageAt() == null ? b.updatedAt() : b.lastMessageAt();
                    return bt.compareTo(at);
                })
                .toList();
    }

    /**
     * 会话文件：从附件消息还原文件名 / 大小 / 类型，私聊和群聊各自独立。
     *
     * <p>只返回「对我可见」的消息：本人清空记录之前的附件不再出现。
     * 文件元信息以 im_file 为准，图片消息本身没存文件名。</p>
     */
    @Transactional(readOnly = true)
    public List<ConversationFileView> listFiles(long userId, String conversationId, int limit) {
        requireMember(conversationId, userId);
        long clearedSeq = conversationRepository.findMemberPrefs(conversationId, userId)
                .map(ConversationMemberPrefs::clearedSeq)
                .orElse(0L);
        List<Message> messages = messageRepository.listMedia(conversationId, clearedSeq,
                Math.max(1, Math.min(limit, 300)));

        Map<Long, Long> fileIdByMessage = new HashMap<>();
        for (Message message : messages) {
            long fileId = fileIdOf(message.content());
            if (fileId > 0) {
                fileIdByMessage.put(message.id(), fileId);
            }
        }
        if (fileIdByMessage.isEmpty()) {
            return List.of();
        }
        Map<Long, StoredFile> files = new HashMap<>();
        fileRepository.findByIds(new ArrayList<>(fileIdByMessage.values()))
                .forEach(file -> files.put(file.id(), file));
        Map<Long, String> remarks = new HashMap<>();
        for (Friend friend : friendRepository.listByUser(userId, 500)) {
            if (friend.remark() != null && !friend.remark().isBlank()) {
                remarks.put(friend.friendId(), friend.remark());
            }
        }
        List<Long> senderIds = messages.stream().map(Message::senderId).distinct().toList();
        Map<Long, User> users = new HashMap<>();
        userRepository.findByIds(senderIds).forEach(user -> users.put(user.id(), user));

        return messages.stream()
                .map(message -> {
                    StoredFile file = files.get(fileIdByMessage.get(message.id()));
                    if (file == null) {
                        return null;
                    }
                    User sender = users.get(message.senderId());
                    String nickname = sender == null ? "用户 " + message.senderId()
                            : sender.nickname();
                    String senderName = remarks.getOrDefault(message.senderId(), nickname);
                    return new ConversationFileView(file.id(), message.id(),
                            message.type().name(), file.originalName(), file.sizeBytes(),
                            file.contentType(), message.senderId(), senderName,
                            message.createdAt());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private long fileIdOf(byte[] content) {
        if (content == null || content.length == 0) {
            return 0L;
        }
        try {
            return JsonUtils.mapper().readTree(content).path("fileId").asLong(0);
        } catch (Exception e) {
            return 0L;
        }
    }

    private String preview(MessageType type, byte[] content, boolean recalled) {
        if (recalled) {
            return "[消息已撤回]";
        }
        String text = switch (type) {
            case TEXT -> new String(content, java.nio.charset.StandardCharsets.UTF_8);
            case EMOJI -> "[表情]";
            case IMAGE -> "[图片]";
            case AUDIO -> "[语音]";
            case VIDEO -> "[视频]";
            case FILE -> "[文件]";
            // 系统消息的内容就是给人看的纯文本（如「爱丽丝 将 鲍勃 禁言 7 分钟」）。
            case SYSTEM -> new String(content, java.nio.charset.StandardCharsets.UTF_8);
        };
        text = text.replaceAll("\\s+", " ").trim();
        // 前端按容器宽度做 CSS 省略；这里只兜住超长文本占内存，不再提前截断。
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    /**
     * 校验会话存在、租户一致且用户是成员。
     */
    @Transactional(readOnly = true)
    public ConversationMember requireMember(String conversationId, long userId) {
        return requireMember(requireConversation(conversationId), userId);
    }

    private ConversationMember requireMember(Conversation conversation, long userId) {
        return conversationRepository.findMember(conversation.id(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
    }

    private Conversation requireConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND,
                        "conversation not found: " + conversationId));
    }

    @Transactional(readOnly = true)
    public ConversationView view(String conversationId, long userId) {
        Conversation conversation = requireConversation(conversationId);
        requireMember(conversation, userId);
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
        boolean advanced = updated.lastReadSeq() != member.lastReadSeq();
        // 打开会话就等于看过：手动标为未读在这里清掉，否则红点永远消不掉。
        conversationRepository.updateMemberManualUnread(conversationId, userId, false);
        if (!advanced) {
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

    /**
     * 会话列表的成员级设置：置顶、免打扰、不显示、手动未读。
     *
     * <p>全部只作用于当前用户，不影响会话里的其他人。</p>
     */
    @Transactional
    public void updateMemberPrefs(long userId, String conversationId, Boolean pinned,
                                  Boolean muted, Boolean hidden, Boolean manuallyUnread) {
        requireMember(conversationId, userId);
        if (pinned != null || muted != null) {
            conversationRepository.updateMemberFlags(conversationId, userId, pinned, muted);
        }
        if (hidden != null) {
            conversationRepository.updateMemberHidden(conversationId, userId,
                    hidden ? timeProvider.now() : null);
        }
        if (manuallyUnread != null) {
            conversationRepository.updateMemberManualUnread(conversationId, userId, manuallyUnread);
        }
    }

    /**
     * 删除聊天记录：把本人可见水位抬到当前最大序号，并标为不显示。
     *
     * <p>消息行不删除，会话另一方的聊天记录完全不受影响；自己再次收到新消息时会话会重新出现。</p>
     */
    @Transactional
    public void clearHistory(long userId, String conversationId) {
        requireMember(conversationId, userId);
        long lastSeq = messageRepository.maxSeq(conversationId);
        conversationRepository.clearMemberHistory(conversationId, userId, lastSeq,
                timeProvider.now());
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
