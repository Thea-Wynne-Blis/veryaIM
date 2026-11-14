package com.veryal.infrastructure.storage;

import com.veryal.common.exception.SystemException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.file.FileStorage;
import com.veryal.domain.file.FileStorageProvider;
import com.veryal.domain.file.StorageType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 按 {@code veryal.storage.type} 选择文件存储策略。
 */
@Component
public class FileStorageProviderImpl implements FileStorageProvider {

    private final Map<StorageType, FileStorage> storages;
    private final StorageType configuredType;

    public FileStorageProviderImpl(List<FileStorage> storageList, StorageProperties properties) {
        Map<StorageType, FileStorage> map = new EnumMap<>(StorageType.class);
        for (FileStorage storage : storageList) {
            if (map.put(storage.type(), storage) != null) {
                throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                        "duplicate file storage strategy: " + storage.type());
            }
        }
        this.configuredType = properties.getType();
        if (!map.containsKey(configuredType)) {
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "file storage strategy not found: " + configuredType);
        }
        this.storages = map;
    }

    @Override
    public FileStorage current() {
        return storages.get(configuredType);
    }

    @Override
    public FileStorage get(StorageType type) {
        FileStorage storage = storages.get(type);
        if (storage == null) {
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "file storage strategy not found: " + type);
        }
        return storage;
    }
}
