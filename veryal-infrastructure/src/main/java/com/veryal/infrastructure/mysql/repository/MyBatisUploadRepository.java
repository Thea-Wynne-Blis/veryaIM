package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.file.UploadRepository;
import com.veryal.domain.file.UploadSession;
import com.veryal.domain.file.UploadStatus;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.UploadSessionMapper;
import com.veryal.infrastructure.mysql.po.UploadSessionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 分片上传会话 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisUploadRepository implements UploadRepository {

    private final UploadSessionMapper uploadSessionMapper;

    @Override
    public void save(UploadSession session) {
        UploadSessionPO po = new UploadSessionPO();
        po.id = session.id();
        po.uploaderId = session.uploaderId();
        po.filename = session.filename();
        po.contentType = session.contentType();
        po.sizeBytes = session.sizeBytes();
        po.chunkSize = session.chunkSize();
        po.totalChunks = session.totalChunks();
        po.conversationId = session.conversationId();
        po.status = session.status().code();
        po.createdAt = SqlTime.toLocal(session.createdAt());
        po.fileId = session.fileId();
        uploadSessionMapper.insert(po);
    }

    @Override
    public Optional<UploadSession> findById(long uploadId) {
        UploadSessionPO po = uploadSessionMapper.selectOne(Wrappers.<UploadSessionPO>lambdaQuery()
                .eq(UploadSessionPO::getId, uploadId));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void markStatus(long uploadId, UploadStatus status, Long fileId) {
        uploadSessionMapper.update(null, Wrappers.<UploadSessionPO>lambdaUpdate()
                .eq(UploadSessionPO::getId, uploadId)
                .set(UploadSessionPO::getStatus, status.code())
                .set(UploadSessionPO::getFileId, fileId));
    }

    private UploadSession toDomain(UploadSessionPO po) {
        return new UploadSession(po.id, po.uploaderId, po.filename,
                po.contentType, po.sizeBytes, po.chunkSize, po.totalChunks, po.conversationId,
                UploadStatus.fromCode(po.status), SqlTime.toInstant(po.createdAt), po.fileId);
    }
}
