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

    /** 会话搜索候选，附件文件名由应用层批量读取文件元信息后匹配。 */
    List<Message> searchCandidates(String conversationId, long afterSeq, long beforeSeq,
                                   MessageSearchFilter filter, int limit);

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
     *
     * @param afterSeq 本人「清空记录」的水位，早于等于它的消息不参与搜索
     */
    List<Message> searchText(String conversationId, String pattern,
                             long afterSeq, long beforeSeq, int limit);

    /**
     * 会话内的附件消息（图片 / 文件 / 语音 / 视频），按 seq 倒序。
     * 「聊天文件」面板用它，已撤回的消息不出现。
     */
    List<Message> listMedia(String conversationId, long afterSeq, int limit);

    /** 跨会话搜索文本 / 表情消息，只限我参与的会话；按创建时间倒序。 */
    List<Message> searchAcrossConversations(long userId, String pattern, int limit);

    /** 跨会话附件候选，只限我参与的会话；按创建时间及 ID 稳定分页。 */
    List<Message> listMediaAcrossConversations(long userId, java.time.Instant beforeTime,
                                              long beforeId, int limit);


    long countAll();
}
