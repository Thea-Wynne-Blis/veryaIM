package com.veryal.domain.file;

/**
 * 当前启用的文件存储策略。
 */
public interface FileStorageProvider {

    FileStorage current();

    FileStorage get(StorageType type);
}
