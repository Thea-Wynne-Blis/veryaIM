package com.veryal.access.http.dto;

import com.veryal.application.favorite.FavoriteView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 收藏列表项。文本消息给 {@code text}，附件消息给 {@code fileId/fileName}。
 */
public record FavoriteResponse(
        @Schema(description = "收藏 ID")
        long id,
        @Schema(description = "来源会话 ID")
        String conversationId,
        @Schema(description = "来源消息 ID")
        long messageId,
        @Schema(description = "消息类型")
        String type,
        @Schema(description = "文本内容，附件消息为 null")
        String text,
        @Schema(description = "附件文件 ID")
        Long fileId,
        @Schema(description = "附件原始文件名")
        String fileName,
        @Schema(description = "附件字节数")
        Long sizeBytes,
        @Schema(description = "附件 MIME 类型")
        String contentType,
        @Schema(description = "发送者 ID")
        long senderId,
        @Schema(description = "发送者显示名（好友备注优先）")
        String senderName,
        @Schema(description = "来源会话标题")
        String conversationTitle,
        @Schema(description = "收藏时间")
        Instant createdAt
) {

    public static FavoriteResponse from(FavoriteView view) {
        return new FavoriteResponse(view.id(), view.conversationId(), view.messageId(),
                view.type(), view.text(), view.fileId(), view.fileName(), view.sizeBytes(),
                view.contentType(), view.senderId(), view.senderName(),
                view.conversationTitle(), view.createdAt());
    }
}
