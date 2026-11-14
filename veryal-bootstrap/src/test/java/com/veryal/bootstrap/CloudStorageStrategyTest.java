package com.veryal.bootstrap;

import com.veryal.common.exception.SystemException;
import com.veryal.domain.file.FileStorage;
import com.veryal.domain.file.FileStorageProvider;
import com.veryal.domain.file.StorageType;
import com.veryal.infrastructure.storage.AliyunOssFileStorage;
import com.veryal.infrastructure.storage.FileStorageProviderImpl;
import com.veryal.infrastructure.storage.LocalFileStorage;
import com.veryal.infrastructure.storage.MinioFileStorage;
import com.veryal.infrastructure.storage.StorageProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 存储策略选择与云端客户端配置测试（不访问外网）。
 */
class CloudStorageStrategyTest {

    @Test
    void selectsStrategyByConfiguredType() {
        StorageProperties properties = new StorageProperties();
        properties.setType(StorageType.MINIO);
        List<FileStorage> storages = List.of(
                new LocalFileStorage(properties),
                new MinioFileStorage(properties),
                new AliyunOssFileStorage(properties));

        FileStorageProvider provider = new FileStorageProviderImpl(storages, properties);

        assertThat(provider.current().type()).isEqualTo(StorageType.MINIO);
        assertThat(provider.get(StorageType.LOCAL).type()).isEqualTo(StorageType.LOCAL);
        assertThatThrownBy(() -> provider.get(null)).isInstanceOf(SystemException.class);
    }

    @Test
    void ossPresignsUrlWithoutNetwork() {
        StorageProperties properties = new StorageProperties();
        properties.setType(StorageType.OSS);
        properties.getOss().setEndpoint("https://oss-cn-test.aliyuncs.com");
        properties.getOss().setAccessKeyId("test-access-key");
        properties.getOss().setAccessKeySecret("test-access-secret");
        properties.getOss().setBucket("veryal-test");

        AliyunOssFileStorage storage = new AliyunOssFileStorage(properties);
        try {
            String url = storage.presignedUrl("tenant/1/a.txt", Duration.ofMinutes(5));
            assertThat(url).contains("veryal-test").contains("a.txt");
        } finally {
            storage.shutdown();
        }
    }

    @Test
    void minioWithoutConfigFailsWhenUsed() {
        StorageProperties properties = new StorageProperties();
        MinioFileStorage storage = new MinioFileStorage(properties);
        assertThatThrownBy(() -> storage.presignedUrl("a.txt", Duration.ofMinutes(1)))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("minio.endpoint");
    }
}
