package com.veryal.infrastructure.mysql.repository;

import com.veryal.domain.message.ConversationSequenceRepository;
import com.veryal.infrastructure.mysql.mapper.ConversationSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 会话序号分配实现。
 *
 * <p>单条 upsert 原子自增（首次插入 1），再用 {@code LAST_INSERT_ID()} 取回新值：
 * 同一会话严格递增，不同会话互不阻塞。行锁只在单条语句内持有，
 * 因此高并发发送同一会话时不会因为多语句事务窗口被串行化。</p>
 */
@Repository
@RequiredArgsConstructor
public class MyBatisConversationSequenceRepository implements ConversationSequenceRepository {

    private final ConversationSequenceMapper sequenceMapper;

    @Override
    public long allocate(String conversationId) {
        sequenceMapper.advance(conversationId);
        return sequenceMapper.lastInsertId();
    }

    @Override
    public long current(String conversationId) {
        Long current = sequenceMapper.selectCurrent(conversationId);
        return current == null ? 0L : current;
    }
}
