package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 已收藏的表情包文件：前端拿 `fileId` 直接发 IMAGE 消息。
 *
 * @param fileId      文件 ID
 * @param name        文件名（服务端清洗后的安全名）
 * @param contentType 图片类型
 * @param sizeBytes   字节数
 * @param width       宽；无法解码时为 0
 * @param height      高；无法解码时为 0
 */
public record StickerFileResponse(
        @Schema(description = "文件 ID")
        long fileId,
        @Schema(description = "文件名")
        String name,
        @Schema(description = "图片类型")
        String contentType,
        @Schema(description = "字节数")
        long sizeBytes,
        @Schema(description = "宽度；无法解码时为 0")
        int width,
        @Schema(description = "高度；无法解码时为 0")
        int height
) {
}
