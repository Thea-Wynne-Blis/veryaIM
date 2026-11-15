package com.veryal.domain.file;

import java.time.Instant;
import java.util.Optional;

/**
 * 文件元数据仓储端口。
 */
public interface FileRepository {

    void save(StoredFile file);

    Optional<StoredFile> findById(long fileId);

    void markDeleted(long fileId, Instant deletedAt);

    long totalBytes();
}
