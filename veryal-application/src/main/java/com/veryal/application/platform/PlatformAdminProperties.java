package com.veryal.application.platform;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 平台管理员与平台 Token 配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.platform")
public class PlatformAdminProperties {

    /** 平台 Token 独立密钥；为空时回退到租户 Token 密钥（但仍使用不同 claims）。 */
    private String tokenSecret;

    private Duration tokenTtl = Duration.ofHours(2);

    private String bootstrapUsername;

    private String bootstrapPassword;

    private String bootstrapDisplayName = "Platform Admin";
}
