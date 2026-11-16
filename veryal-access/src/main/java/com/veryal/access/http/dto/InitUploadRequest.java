package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/**
 * 初始化分片上传。
 */
public record InitUploadRequest(
                                 @Schema(description = "文件名")
                                 @NotBlank String filename,
                                 String contentType,
                                 @Positive long sizeBytes,
                                 @Schema(description = "会话 ID。单聊形如 p_{用户A}_{用户B}，群聊即群 ID")
                                 String conversationId
) {
}
