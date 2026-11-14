package com.veryal.infrastructure.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;
import com.veryal.domain.file.FileStorage;
import com.veryal.domain.file.StorageType;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 阿里云 OSS 存储策略。
 */
@Component
public class AliyunOssFileStorage implements FileStorage {

    private final StorageProperties properties;
    private volatile OSS client;

    public AliyunOssFileStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StorageType type() {
        return StorageType.OSS;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        try (InputStream input = new ByteArrayInputStream(content)) {
            client().putObject(bucket(), objectKey, input, metadata);
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to put OSS object", e);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            OSSObject object = client().getObject(bucket(), objectKey);
            try (InputStream input = object.getObjectContent()) {
                return input.readAllBytes();
            }
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file object not found");
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to get OSS object", e);
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to get OSS object", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().deleteObject(bucket(), objectKey);
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to delete OSS object", e);
        }
    }

    @Override
    public String presignedUrl(String objectKey, Duration ttl) {
        try {
            Date expiration = Date.from(Instant.now().plus(ttl));
            return client().generatePresignedUrl(bucket(), objectKey, expiration).toString();
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to sign OSS url", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        OSS current = client;
        if (current != null) {
            current.shutdown();
        }
    }

    private OSS client() {
        OSS current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Oss oss = properties.getOss();
                    require(oss.getEndpoint(), "veryal.storage.oss.endpoint");
                    require(oss.getAccessKeyId(), "veryal.storage.oss.access-key-id");
                    require(oss.getAccessKeySecret(), "veryal.storage.oss.access-key-secret");
                    client = new OSSClientBuilder().build(oss.getEndpoint(),
                            oss.getAccessKeyId(), oss.getAccessKeySecret());
                }
                current = client;
            }
        }
        return current;
    }

    private String bucket() {
        String bucket = properties.getOss().getBucket();
        require(bucket, "veryal.storage.oss.bucket");
        return bucket;
    }

    private void require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "storage is set to oss but " + property + " is empty");
        }
    }
}
