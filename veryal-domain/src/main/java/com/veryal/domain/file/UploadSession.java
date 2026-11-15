package com.veryal.domain.file;

import java.time.Instant;

/**
 * 分片上传会话。分片数据暂存于本机，完成后合成为文件对象。
 */
public record UploadSession(long id, long tenantId, long uploaderId, String filename, String contentType,
                            long sizeBytes, int chunkSize, int totalChunks, String conversationId,
                            UploadStatus status, Instant createdAt, Long fileId) {

    public UploadSession {
        if (id <= 0 || tenantId <= 0 || uploaderId <= 0) {
            throw new IllegalArgumentException("upload ids must be positive");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (sizeBytes <= 0 || chunkSize <= 0 || totalChunks <= 0) {
            throw new IllegalArgumentException("invalid upload size/chunk count");
        }
        if (status == null || createdAt == null) {
            throw new IllegalArgumentException("status/createdAt must not be null");
        }
    }
}
