package com.veryal.domain.message;

/**
 * 会话序号分配端口。
 *
 * <p>实现必须保证同一会话内序号严格递增且不重复；调用方必须处于数据库事务中。</p>
 */
public interface ConversationSequenceRepository {

    /**
     * 原子分配下一个会话序号。
     */
    long allocate(long tenantId, String conversationId);

    /**
     * 读取当前最大序号，不存在时返回 0。
     */
    long current(long tenantId, String conversationId);
}
