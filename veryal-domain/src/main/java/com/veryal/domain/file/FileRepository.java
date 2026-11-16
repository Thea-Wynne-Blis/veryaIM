package com.veryal.domain.file;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文件元数据仓储端口。
 */
public interface FileRepository {

    void save(StoredFile file);

    Optional<StoredFile> findById(long fileId);

    /** 某人上传的文件，按时间倒序。 */
    List<StoredFile> findByUploader(long uploaderId, int limit);

    void markDeleted(long fileId, Instant deletedAt);

    long totalBytes();
}
