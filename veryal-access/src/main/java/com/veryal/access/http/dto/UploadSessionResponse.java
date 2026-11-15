package com.veryal.access.http.dto;

import com.veryal.application.file.ChunkedUploadService;

import java.util.List;

/**
 * 分片上传会话状态。
 */
public record UploadSessionResponse(long uploadId, int chunkSize, int totalChunks,
                                   List<Integer> uploadedChunks, String status, Long fileId) {

    public static UploadSessionResponse from(ChunkedUploadService.UploadSessionView view) {
        return new UploadSessionResponse(view.uploadId(), view.chunkSize(), view.totalChunks(),
                view.uploadedChunks(), view.status(), view.fileId());
    }
}
