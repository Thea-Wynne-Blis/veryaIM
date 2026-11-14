package com.veryal.application.message;

import com.veryal.domain.message.Message;

import java.util.List;

/**
 * 发送结果。{@code duplicate=true} 表示命中幂等，返回的是已存在的消息。
 */
public record SendMessageResult(Message message, List<Long> recipientUserIds, boolean duplicate) {
}
