package com.veryal.infrastructure.storage;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;
import com.veryal.domain.file.FileStorage;
import com.veryal.domain.file.StorageType;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;

/**
 * MinIO / S3 兼容对象存储策略。
 */
@Component
public class MinioFileStorage implements FileStorage {

    private static final int MAX_PRESIGN_SECONDS = 7 * 24 * 60 * 60;

    private final StorageProperties properties;
    private volatile MinioClient client;
    private volatile MinioClient signing;
    private volatile boolean bucketReady;

    public MinioFileStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StorageType type() {
        return StorageType.MINIO;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            ensureBucket();
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to put MinIO object", e);
        }
    }

    @Override
    public void put(String objectKey, InputStream content, long size, String contentType) {
        try (InputStream input = content) {
            ensureBucket();
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(input, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to put MinIO object", e);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        return open(objectKey, 0, 0);
    }

    /** MinIO 原生支持 offset/length，Range 请求不必先丢弃前缀。 */
    @Override
    public InputStream open(String objectKey, long offset, long length) {
        GetObjectArgs.Builder builder = GetObjectArgs.builder()
                .bucket(bucket()).object(objectKey);
        if (offset > 0) {
            builder.offset(offset);
        }
        if (length > 0) {
            builder.length(length);
        }
        try {
            return client().getObject(builder.build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file object not found");
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to get MinIO object", e);
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to get MinIO object", e);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try (GetObjectResponse response = client().getObject(GetObjectArgs.builder()
                .bucket(bucket()).object(objectKey).build())) {
            return response.readAllBytes();
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file object not found");
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to get MinIO object", e);
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to get MinIO object", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket()).object(objectKey).build());
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to delete MinIO object", e);
        }
    }

    @Override
    public String presignedUrl(String objectKey, Duration ttl) {
        // 先校验内网连接配置，避免「少配了 key」被静默当成「不支持签名」。
        client();
        String publicEndpoint = properties.getMinio().getPublicEndpoint();
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            return null;
        }
        try {
            int expiry = (int) Math.min(MAX_PRESIGN_SECONDS, Math.max(1, ttl.toSeconds()));
            return signingClient(publicEndpoint).getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket())
                    .object(objectKey)
                    .expiry(expiry)
                    .build());
        } catch (Exception e) {
            if (e instanceof SystemException systemException) {
                throw systemException;
            }
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to sign MinIO url", e);
        }
    }

    /** 签名用的客户端：endpoint 换成客户端可达的公网地址，凭据与桶不变。 */
    private MinioClient signingClient(String publicEndpoint) {
        MinioClient current = signing;
        if (current == null) {
            synchronized (this) {
                if (signing == null) {
                    StorageProperties.Minio minio = properties.getMinio();
                    require(minio.getAccessKey(), "veryal.storage.minio.access-key");
                    require(minio.getSecretKey(), "veryal.storage.minio.secret-key");
                    signing = MinioClient.builder()
                            .endpoint(publicEndpoint)
                            .region(minio.getRegion())
                            .credentials(minio.getAccessKey(), minio.getSecretKey())
                            .build();
                }
                current = signing;
            }
        }
        return current;
    }

    private MinioClient client() {
        MinioClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Minio minio = properties.getMinio();
                    require(minio.getEndpoint(), "veryal.storage.minio.endpoint");
                    require(minio.getAccessKey(), "veryal.storage.minio.access-key");
                    require(minio.getSecretKey(), "veryal.storage.minio.secret-key");
                    client = MinioClient.builder()
                            .endpoint(minio.getEndpoint())
                            .region(minio.getRegion())
                            .credentials(minio.getAccessKey(), minio.getSecretKey())
                            .build();
                }
                current = client;
            }
        }
        return current;
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            String bucket = bucket();
            if (!client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady = true;
        }
    }

    private String bucket() {
        String bucket = properties.getMinio().getBucket();
        require(bucket, "veryal.storage.minio.bucket");
        return bucket;
    }

    private void require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "storage is set to minio but " + property + " is empty");
        }
    }
}
