package com.veryal.domain.file;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 分片上传会话音量仓储端口。
 */
public interface UploadRepository {

    void save(UploadSession session);

    Optional<UploadSession> findById(long uploadId);

    /** 早于给定时间仍未完成的会话，用于清理放弃的上传。 */
    List<UploadSession> findActiveBefore(Instant createdBefore, int limit);

    void markStatus(long uploadId, UploadStatus status, Long fileId);
}
