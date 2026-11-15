package com.veryal.application.file;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分片上传配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.upload")
public class UploadProperties {

    private String stagingPath = "./data/uploads";

    private int chunkSizeBytes = 5 * 1024 * 1024;

    private long maxSizeBytes = 64L * 1024 * 1024;
}
