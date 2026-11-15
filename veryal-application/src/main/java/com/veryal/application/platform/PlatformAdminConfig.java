package com.veryal.application.platform;

import com.veryal.application.identity.TokenProperties;
import com.veryal.common.core.TimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 平台管理员 Token 装配。
 */
@Configuration
public class PlatformAdminConfig {

    @Bean
    public PlatformTokenService platformTokenService(PlatformAdminProperties properties,
                                                     TokenProperties tokenProperties,
                                                     TimeProvider timeProvider) {
        return new PlatformTokenService(properties, tokenProperties, timeProvider);
    }
}
