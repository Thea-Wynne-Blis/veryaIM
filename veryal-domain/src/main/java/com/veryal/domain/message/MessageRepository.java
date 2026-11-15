package com.veryal.domain.message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 消息仓储端口。
 *
 * <p>消息写入必须使用游标分页读取，禁止 OFFSET。</p>
 */
public interface MessageRepository {

    Optional<Message> findById(long messageId);

    Optional<Message> findByIdempotencyKey(long senderId, String clientMsgId);

    void append(Message message);

    List<Message> pageAfter(String conversationId, long afterSeq, int limit);

    Optional<Message> findLatest(String conversationId);

    long maxSeq(String conversationId);

    /**
     * 标记消息撤回；幂等，已撤回时保持原值。
     */
    void markRecalled(long messageId, long recalledBy, Instant recalledAt);

    /**
     * 会话内文本/表情搜索，按 seq 倒序游标分页；已撤回消息不参与搜索。
     */
    List<Message> searchText(String conversationId, String pattern,
                             long beforeSeq, int limit);


    long countAll();
}
