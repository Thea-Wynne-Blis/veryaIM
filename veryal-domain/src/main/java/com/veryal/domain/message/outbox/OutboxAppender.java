package com.veryal.domain.message.outbox;

/**
 * Outbox 写入端口。
 *
 * <p>必须在业务事务内调用，保证“消息落库”与“事件落库”原子性。</p>
 */
public interface OutboxAppender {

    void append(OutboxMessage message);
}
