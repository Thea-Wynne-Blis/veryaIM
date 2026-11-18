package com.veryal.application.message;

import com.veryal.common.core.RandomIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.message.ConversationSequenceRepository;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.MessageType;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.domain.message.event.MessageEvents;
import com.veryal.domain.message.outbox.OutboxAppender;
import com.veryal.domain.message.outbox.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 服务端系统消息（入群、禁言提示等）。
 *
 * <p>和普通消息走同一套持久化与 Outbox 投递：占一个会话序号、更新会话列表预览，
 * 并通过 MQ 实时推给在线成员。系统消息只能由服务端产生，客户端提交会被
 * {@code SystemMessageStrategy} 拒绝。</p>
 */
@Service
@RequiredArgsConstructor
public class SystemMessageService {

    private final MessageRepository messageRepository;
    private final ConversationSequenceRepository sequenceRepository;
    private final ConversationRepository conversationRepository;
    private final OutboxAppender outboxAppender;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    /**
     * 往群会话里追加一条系统提示。
     *
     * @param conversationId 群 ID 即会话 ID
     * @param actorId        触发这次操作的用户（用于归属与已读水位）
     * @param text           展示在聊天框里的纯文本
     */
    @Transactional
    public Message appendGroupNotice(String conversationId, long actorId, String text) {
        Instant now = timeProvider.now();
        long seq = sequenceRepository.allocate(conversationId);
        long messageId = idGenerator.nextId();
        Message message = Message.create(messageId, conversationId, actorId,
                "sys-" + messageId, seq, MessageType.SYSTEM,
                text.getBytes(StandardCharsets.UTF_8), now);
        messageRepository.append(message);

        MessageCreatedEvent event = new MessageCreatedEvent(RandomIds.newId(),
                MessageEvents.GROUP_CREATED, conversationId, messageId, actorId,
                null, seq, MessageType.SYSTEM.code(), message.content(), now,
                message.clientMsgId(), List.of(), null, null, null);
        outboxAppender.append(new OutboxMessage(event.eventId(), conversationId,
                event.eventType(), JsonUtils.toJsonBytes(event), now));

        // 操作者自己不需要为这条提示多一个未读：把已读水位推到它。
        conversationRepository.advanceReadCursor(conversationId, actorId, seq);
        return message;
    }
}
