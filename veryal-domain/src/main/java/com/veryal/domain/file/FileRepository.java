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

    void markDeleted(long fileId, Instant deletedAt);

    /**
     * 孤儿文件：上传后一直没被动态、收藏或头像引用，且已超过保留时间。
     *
     * <p>典型来源是「选了图又关掉发布框」和「删掉的动态留下媒体」；
     * 会话附件带 conversationId，不在清理范围内。</p>
     */
    List<StoredFile> findOrphans(Instant createdBefore, int limit);

    long totalBytes();
}
