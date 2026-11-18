package com.veryal.access.http.dto;

import com.veryal.application.conversation.ConversationFileView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 会话文件列表项。
 */
public record ConversationFileResponse(
        @Schema(description = "文件 ID")
        long fileId,
        @Schema(description = "来源消息 ID")
        long messageId,
        @Schema(description = "消息类型：IMAGE / FILE / AUDIO / VIDEO")
        String type,
        @Schema(description = "原始文件名")
        String name,
        @Schema(description = "字节数")
        long sizeBytes,
        @Schema(description = "MIME 类型")
        String contentType,
        @Schema(description = "发送者 ID")
        long senderId,
        @Schema(description = "发送者显示名（好友备注优先）")
        String senderName,
        @Schema(description = "发送时间")
        Instant createdAt
) {

    public static ConversationFileResponse from(ConversationFileView view) {
        return new ConversationFileResponse(view.fileId(), view.messageId(), view.messageType(),
                view.name(), view.sizeBytes(), view.contentType(), view.senderId(),
                view.senderName(), view.createdAt());
    }
}
