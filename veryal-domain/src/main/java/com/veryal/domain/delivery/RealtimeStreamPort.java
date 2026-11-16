package com.veryal.domain.delivery;

import com.veryal.domain.message.event.MessageCreatedEvent;

/**
 * 浏览器实时事件流端口。
 *
 * <p>接入层实现，把消息事件推送给以长连接（SSE）订阅的网页端；
 * 与 Netty 连接推送互补，不依赖 MQ。</p>
 */
public interface RealtimeStreamPort {

    void publish(MessageCreatedEvent event);
}
