package com.veryal.infrastructure.mysql.repository;

import com.veryal.domain.message.ConversationSequenceRepository;
import com.veryal.infrastructure.mysql.mapper.ConversationSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 会话序号分配实现。
 *
 * <p>同一事务内：INSERT IGNORE 初始化 → SELECT ... FOR UPDATE 锁定行 → UPDATE 递增。
 * 行锁保证同一会话的序号严格递增；不同会话互不阻塞。</p>
 */
@Repository
@RequiredArgsConstructor
public class MyBatisConversationSequenceRepository implements ConversationSequenceRepository {

    private final ConversationSequenceMapper sequenceMapper;

    @Override
    public long allocate(String conversationId) {
        sequenceMapper.insertIgnore(conversationId);
        long current = sequenceMapper.selectForUpdate(conversationId);
        long next = current + 1;
        sequenceMapper.updateSeq(conversationId, next);
        return next;
    }

    @Override
    public long current(String conversationId) {
        Long current = sequenceMapper.selectCurrent(conversationId);
        return current == null ? 0L : current;
    }
}
