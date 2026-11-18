package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.FileStatus;
import com.veryal.domain.file.StorageType;
import com.veryal.domain.file.StoredFile;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.FileMapper;
import com.veryal.infrastructure.mysql.po.FilePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文件元数据 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisFileRepository implements FileRepository {

    private final FileMapper fileMapper;

    @Override
    public void save(StoredFile file) {
        FilePO po = new FilePO();
        po.id = file.id();
        po.uploaderId = file.uploaderId();
        po.objectKey = file.objectKey();
        po.originalName = file.originalName();
        po.contentType = file.contentType();
        po.sizeBytes = file.sizeBytes();
        po.sha256 = file.sha256();
        po.storageType = file.storageType().name();
        po.status = file.status().code();
        po.createdAt = SqlTime.toLocal(file.createdAt());
        po.deletedAt = file.deletedAt() == null ? null : SqlTime.toLocal(file.deletedAt());
        po.conversationId = file.conversationId();
        fileMapper.insert(po);
    }

    @Override
    public Optional<StoredFile> findById(long fileId) {
        FilePO po = fileMapper.selectOne(Wrappers.<FilePO>lambdaQuery()
                .eq(FilePO::getId, fileId));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<StoredFile> findByIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = fileIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return fileMapper.selectBatchIds(ids).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void markDeleted(long fileId, Instant deletedAt) {
        fileMapper.update(null, Wrappers.<FilePO>lambdaUpdate()
                .eq(FilePO::getId, fileId)
                .set(FilePO::getStatus, FileStatus.DELETED.code())
                .set(FilePO::getDeletedAt, SqlTime.toLocal(deletedAt)));
    }

    @Override
    public long totalBytes() {
        return fileMapper.selectTotalBytes();
    }

    @Override
    public List<StoredFile> findByUploader(long uploaderId, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        return fileMapper.selectList(Wrappers.<FilePO>lambdaQuery()
                        .eq(FilePO::getUploaderId, uploaderId)
                        .eq(FilePO::getStatus, FileStatus.ACTIVE.code())
                        .isNull(FilePO::getDeletedAt)
                        .orderByDesc(FilePO::getId)
                        .last("limit " + size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private StoredFile toDomain(FilePO po) {
        return new StoredFile(po.id, po.uploaderId, po.objectKey,
                po.originalName, po.contentType, po.sizeBytes, po.sha256,
                StorageType.valueOf(po.storageType),
                FileStatus.fromCode(po.status), SqlTime.toInstant(po.createdAt),
                po.deletedAt == null ? null : SqlTime.toInstant(po.deletedAt), po.conversationId);
    }
}
