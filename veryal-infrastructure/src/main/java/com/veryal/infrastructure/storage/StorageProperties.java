package com.veryal.infrastructure.storage;

import com.veryal.domain.file.StorageType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置。type 选择策略实现：local / minio / oss。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.storage")
public class StorageProperties {

    private StorageType type = StorageType.LOCAL;

    private Local local = new Local();

    private Minio minio = new Minio();

    private Oss oss = new Oss();

    @Getter
    @Setter
    public static class Local {

        private String rootPath = "./data/files";
    }

    @Getter
    @Setter
    public static class Minio {

        /** 后端访问对象存储用的地址，通常是 127.0.0.1 或集群内域名。 */
        private String endpoint;

        /**
         * 客户端访问对象存储用的公网地址，仅用于签名临时 URL。
         *
         * <p>签名（SigV4）把 Host 算进签名串，所以内网 endpoint 签出来的地址客户端用不了。
         * 不配置时 {@link MinioFileStorage#presignedUrl} 返回 {@code null}，调用方回退到
         * {@code /api/v1/files/{id}/content} 走应用流式下载（只暴露一个对外的 8899 端口）。
         */
        private String publicEndpoint;

        private String accessKey;

        private String secretKey;

        /**
         * 签名 region。必须显式配置：不配时 minio-java 会先发一次
         * {@code GET /{bucket}?location=} 去问服务端，等于签名也要网络往返，
         * 而且拿不到就会签名失败。Garage / MinIO 默认都用 us-east-1。
         */
        private String region = "us-east-1";

        private String bucket = "veryal";
    }

    @Getter
    @Setter
    public static class Oss {

        private String endpoint;

        private String accessKeyId;

        private String accessKeySecret;

        private String bucket = "veryal";
    }
}
