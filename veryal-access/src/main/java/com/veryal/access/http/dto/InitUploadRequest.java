package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 初始化分片上传。
 */
public record InitUploadRequest(@NotBlank String filename, String contentType,
                                @Positive long sizeBytes, String conversationId) {
}
