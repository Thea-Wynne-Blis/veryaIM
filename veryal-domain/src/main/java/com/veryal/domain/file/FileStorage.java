package com.veryal.domain.file;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * 文件存储策略接口。实现由存储类型选择：本地磁盘 / MinIO / 阿里云 OSS。
 *
 * <p>大文件（视频、音频）不能整份读进堆：写入用 {@link #put(String, InputStream, long, String)}，
 * 读取用 {@link #open(String, long, long)}，由接入层按 Range 分块回写。</p>
 */
public interface FileStorage {

    StorageType type();

    void put(String objectKey, byte[] content, String contentType);

    /**
     * 流式写入。默认实现退回一次性写入，本地/对象存储实现应覆盖以保持低内存占用。
     */
    default void put(String objectKey, InputStream content, long size, String contentType) {
        try (InputStream input = content) {
            put(objectKey, input.readAllBytes(), contentType);
        } catch (IOException e) {
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to read upload stream", e);
        }
    }

    byte[] get(String objectKey);

    /**
     * 流式读取，调用方负责关闭。
     */
    default InputStream open(String objectKey) {
        return new ByteArrayInputStream(get(objectKey));
    }

    /**
     * 从 {@code offset} 起读取 {@code length} 字节；{@code length <= 0} 表示读到结尾。
     * Range 请求靠它避免把整段前缀读出来丢掉。
     */
    default InputStream open(String objectKey, long offset, long length) {
        InputStream input = open(objectKey);
        try {
            if (offset > 0) {
                input.skipNBytes(offset);
            }
            return input;
        } catch (IOException e) {
            closeQuietly(input);
            throw new SystemException(ErrorCode.STORAGE_ERROR,
                    "failed to seek file object", e);
        }
    }

    void delete(String objectKey);

    /**
     * 生成临时访问 URL；本地存储无法提供时返回 null。
     */
    String presignedUrl(String objectKey, Duration ttl);

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // 已经是错误路径，关闭失败不再覆盖原始异常
        }
    }
}
