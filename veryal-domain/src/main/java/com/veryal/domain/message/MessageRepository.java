package com.veryal.domain.message;

import java.util.List;
import java.util.Optional;

/**
 * 消息仓储端口。
 *
 * <p>消息写入必须使用游标分页读取，禁止 OFFSET。</p>
 */
public interface MessageRepository {

    Optional<Message> findById(long messageId);

    Optional<Message> findByIdempotencyKey(long tenantId, long senderId, String clientMsgId);

    void append(Message message);

    List<Message> pageAfter(long tenantId, String conversationId, long afterSeq, int limit);

    Optional<Message> findLatest(long tenantId, String conversationId);

    long maxSeq(long tenantId, String conversationId);
}
