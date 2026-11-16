package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.file.ChunkedUploadService;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 分片上传会话状态。
 */
public record UploadSessionResponse(
                                 @Schema(description = "分片上传会话 ID")
                                 long uploadId,
                                 @Schema(description = "单个分片大小（字节）")
                                 int chunkSize,
                                 int totalChunks,
                                 @Schema(description = "已接收的分片序号列表")
                                 List<Integer> uploadedChunks,
                                 @Schema(description = "状态")
                                 String status,
                                 @Schema(description = "文件 ID")
                                 Long fileId
) {

    public static UploadSessionResponse from(ChunkedUploadService.UploadSessionView view) {
        return new UploadSessionResponse(view.uploadId(), view.chunkSize(), view.totalChunks(),
                view.uploadedChunks(), view.status(), view.fileId());
    }
}
