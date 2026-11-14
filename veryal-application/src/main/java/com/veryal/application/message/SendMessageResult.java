package com.veryal.application.message;

import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.domain.message.Message;

/**
 * 发送结果。{@code duplicate=true} 表示命中幂等，返回的是已存在的消息。
 *
 * <p>首次成功写入时携带 {@link MessageCreatedEvent}，供本地直投或 Outbox 发布使用；
 * 幂等重发不产生新事件。</p>
 */
public record SendMessageResult(Message message, MessageCreatedEvent event, boolean duplicate) {
}
