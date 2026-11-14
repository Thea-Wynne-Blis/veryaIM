package com.veryal.domain.file;

import java.time.Instant;

/**
 * 文件元数据。文件本体存放在对象存储，MySQL 只保存引用。
 */
public record StoredFile(long id, long tenantId, long uploaderId, String objectKey,
                         String originalName, String contentType, long sizeBytes, String sha256,
                         StorageType storageType, FileStatus status, Instant createdAt,
                         Instant deletedAt) {

    public StoredFile {
        if (id <= 0 || tenantId <= 0 || uploaderId <= 0) {
            throw new IllegalArgumentException("file ids must be positive");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("originalName must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 must not be blank");
        }
        if (storageType == null || status == null || createdAt == null) {
            throw new IllegalArgumentException("storageType/status/createdAt must not be null");
        }
    }
}
