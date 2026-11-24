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

    @Test
    void minioWithoutPublicEndpointFallsBackToStreaming() {
        StorageProperties properties = new StorageProperties();
        properties.getMinio().setEndpoint("http://127.0.0.1:9100");
        properties.getMinio().setAccessKey("test-access-key");
        properties.getMinio().setSecretKey("test-secret-key");

        MinioFileStorage storage = new MinioFileStorage(properties);

        // 只有内网 endpoint 时，签名地址客户端不可达：返回 null，由 Controller 回退到 /content。
        assertThat(storage.presignedUrl("a.txt", Duration.ofMinutes(5))).isNull();
    }

    @Test
    void minioWithPublicEndpointSignsForPublicHost() {
        StorageProperties properties = new StorageProperties();
        properties.getMinio().setEndpoint("http://127.0.0.1:9100");
        properties.getMinio().setPublicEndpoint("https://files.example.com");
        properties.getMinio().setAccessKey("test-access-key");
        properties.getMinio().setSecretKey("test-secret-key");
        properties.getMinio().setBucket("veryal-test");

        MinioFileStorage storage = new MinioFileStorage(properties);

        // 纯本地签名，不发网络请求；host 必须是客户端可达的公网地址。
        assertThat(storage.presignedUrl("tenant/1/a.txt", Duration.ofMinutes(5)))
                .contains("https://files.example.com")
                .contains("veryal-test")
                .contains("a.txt");
    }
}
