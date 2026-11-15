package com.veryal.domain.message.event;

/**
 * 已读回执推送端口，由 access 层实现为向本节点连接写 READ_NOTIFY。
 */
public interface ReadReceiptPushPort {

    void push(ReadReceiptEvent event);
}
