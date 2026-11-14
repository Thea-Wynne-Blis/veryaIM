package com.veryal.application.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Token 签发配置。
 */
@ConfigurationProperties(prefix = "veryal.security.token")
public record TokenProperties(String secret, String issuer, Duration ttl) {

    public TokenProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("veryal.security.token.secret must be at least 32 characters");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "veryal";
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofDays(7);
        }
    }
}
