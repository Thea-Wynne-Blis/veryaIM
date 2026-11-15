package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.message.DuplicateMessageException;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.MessageType;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.MessageMapper;
import com.veryal.infrastructure.mysql.po.MessagePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 消息仓储 MyBatis-Plus 实现。
 *
 * <p>唯一键 {@code uk_sender_client_msg} 承担幂等兜底；冲突时抛出领域异常，由应用层回查已存在消息。</p>
 */
@Repository
@RequiredArgsConstructor
public class MyBatisMessageRepository implements MessageRepository {

    private static final int MAX_PAGE_LIMIT = 1000;

    private final MessageMapper messageMapper;

    @Override
    public Optional<Message> findById(long messageId) {
        return Optional.ofNullable(messageMapper.selectById(messageId)).map(this::toDomain);
    }

    @Override
    public Optional<Message> findByIdempotencyKey(long tenantId, long senderId, String clientMsgId) {
        return Optional.ofNullable(messageMapper.selectOne(Wrappers.<MessagePO>lambdaQuery()
                        .eq(MessagePO::getTenantId, tenantId)
                        .eq(MessagePO::getSenderId, senderId)
                        .eq(MessagePO::getClientMsgId, clientMsgId)))
                .map(this::toDomain);
    }

    @Override
    public void append(Message message) {
        try {
            messageMapper.insert(toPO(message));
        } catch (DuplicateKeyException e) {
            throw new DuplicateMessageException(message.tenantId(), message.senderId(), message.clientMsgId());
        }
    }

    @Override
    public List<Message> pageAfter(long tenantId, String conversationId, long afterSeq, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_LIMIT));
        return messageMapper.selectPageAfter(tenantId, conversationId, afterSeq, safeLimit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Message> findLatest(long tenantId, String conversationId) {
        return Optional.ofNullable(messageMapper.selectOne(Wrappers.<MessagePO>lambdaQuery()
                        .eq(MessagePO::getTenantId, tenantId)
                        .eq(MessagePO::getConversationId, conversationId)
                        .orderByDesc(MessagePO::getSeq)
                        .last("LIMIT 1")))
                .map(this::toDomain);
    }

    @Override
    public long maxSeq(long tenantId, String conversationId) {
        return messageMapper.selectMaxSeq(tenantId, conversationId);
    }

    @Override
    public void markRecalled(long tenantId, long messageId, long recalledBy, java.time.Instant recalledAt) {
        messageMapper.update(null, Wrappers.<MessagePO>lambdaUpdate()
                .eq(MessagePO::getTenantId, tenantId)
                .eq(MessagePO::getId, messageId)
                .isNull(MessagePO::getRecalledAt)
                .set(MessagePO::getRecalledAt, SqlTime.toLocal(recalledAt))
                .set(MessagePO::getRecalledBy, recalledBy));
    }

    @Override
    public List<Message> searchText(long tenantId, String conversationId, String pattern,
                                    long beforeSeq, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_LIMIT));
        return messageMapper.searchText(tenantId, conversationId, pattern, beforeSeq, safeLimit).stream()
                .map(this::toDomain)
                .toList();
    }

    private Message toDomain(MessagePO po) {
        return new Message(po.id, po.tenantId, po.conversationId, po.senderId, po.clientMsgId, po.seq,
                MessageType.fromCode(po.msgType), po.content, SqlTime.toInstant(po.createdAt),
                po.recalledAt == null ? null : SqlTime.toInstant(po.recalledAt), po.recalledBy);
    }

    private MessagePO toPO(Message message) {
        MessagePO po = new MessagePO();
        po.id = message.id();
        po.tenantId = message.tenantId();
        po.conversationId = message.conversationId();
        po.senderId = message.senderId();
        po.clientMsgId = message.clientMsgId();
        po.seq = message.seq();
        po.msgType = message.type().code();
        po.content = message.content();
        po.createdAt = SqlTime.toLocal(message.createdAt());
        return po;
    }
}
