package com.veryal.infrastructure.mysql.repository;

import com.veryal.domain.message.outbox.OutboxAppender;
import com.veryal.domain.message.outbox.OutboxMessage;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.OutboxMapper;
import com.veryal.infrastructure.mysql.po.OutboxPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Outbox 写入实现，由消息事务调用。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisOutboxAppender implements OutboxAppender {

    private final OutboxMapper outboxMapper;

    @Override
    public void append(OutboxMessage message) {
        OutboxPO po = new OutboxPO();
        po.eventId = message.eventId();
        po.aggregateId = message.aggregateId();
        po.eventType = message.eventType();
        po.payload = message.payload();
        po.status = 0;
        po.retryCount = 0;
        po.nextRetryAt = SqlTime.toLocal(message.createdAt());
        po.createdAt = SqlTime.toLocal(message.createdAt());
        po.updatedAt = SqlTime.toLocal(message.createdAt());
        outboxMapper.insert(po);
    }
}
