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

        private String endpoint;

        private String accessKey;

        private String secretKey;

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
