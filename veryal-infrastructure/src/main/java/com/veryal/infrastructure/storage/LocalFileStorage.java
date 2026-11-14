package com.veryal.infrastructure.storage;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;
import com.veryal.domain.file.FileStorage;
import com.veryal.domain.file.StorageType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 本地磁盘存储策略，适用于开发、测试与单机部署。
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        this.root = Paths.get(properties.getLocal().getRootPath()).toAbsolutePath().normalize();
    }

    @Override
    public StorageType type() {
        return StorageType.LOCAL;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "veryal-", ".tmp");
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to write local file", e);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        Path target = resolve(objectKey);
        try {
            return Files.readAllBytes(target);
        } catch (NoSuchFileException e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file object not found");
        } catch (IOException e) {
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to read local file", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to delete local file", e);
        }
    }

    @Override
    public String presignedUrl(String objectKey, Duration ttl) {
        return null;
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid object key");
        }
        return target;
    }
}
