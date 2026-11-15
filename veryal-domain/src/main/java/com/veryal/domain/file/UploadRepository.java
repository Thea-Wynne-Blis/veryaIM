package com.veryal.domain.file;

import java.util.Optional;

/**
 * 分片上传会话音量仓储端口。
 */
public interface UploadRepository {

    void save(UploadSession session);

    Optional<UploadSession> findById(long uploadId);

    void markStatus(long uploadId, UploadStatus status, Long fileId);
}
