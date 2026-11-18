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

    /** 批量取文件元信息，用于会话文件列表一次补齐文件名 / 大小 / 类型。 */
    List<StoredFile> findByIds(List<Long> fileIds);

    /** 某人上传的文件，按时间倒序。 */
    List<StoredFile> findByUploader(long uploaderId, int limit);

    void markDeleted(long fileId, Instant deletedAt);

    long totalBytes();
}
