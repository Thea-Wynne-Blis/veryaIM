package com.veryal.application.file;

import com.veryal.domain.file.StoredFile;

import java.time.Instant;

/**
 * 文件元数据视图。
 */
public record FileView(long fileId, String name, String contentType, long sizeBytes, String sha256,
                       String storageType, Instant createdAt) {

    public static FileView from(StoredFile file) {
        return new FileView(file.id(), file.originalName(), file.contentType(), file.sizeBytes(),
                file.sha256(), file.storageType().name(), file.createdAt());
    }
}
