package com.veryal.access.http.dto;

import com.veryal.application.file.FileView;

import java.time.Instant;

/**
 * 文件上传/查询响应。
 */
public record FileResponse(long fileId, String name, String contentType, long sizeBytes, String sha256,
                           String storageType, Instant createdAt) {

    public static FileResponse from(FileView view) {
        return new FileResponse(view.fileId(), view.name(), view.contentType(), view.sizeBytes(),
                view.sha256(), view.storageType(), view.createdAt());
    }
}
