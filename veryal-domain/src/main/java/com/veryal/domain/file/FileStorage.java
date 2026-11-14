package com.veryal.domain.file;

import java.time.Duration;

/**
 * 文件存储策略接口。实现由存储类型选择：本地磁盘 / MinIO / 阿里云 OSS。
 */
public interface FileStorage {

    StorageType type();

    void put(String objectKey, byte[] content, String contentType);

    byte[] get(String objectKey);

    void delete(String objectKey);

    /**
     * 生成临时访问 URL；本地存储无法提供时返回 null。
     */
    String presignedUrl(String objectKey, Duration ttl);
}
