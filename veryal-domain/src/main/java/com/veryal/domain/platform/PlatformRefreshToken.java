package com.veryal.domain.platform;

import java.time.Instant;

/**
 * 平台管理员 Refresh Token（只保存哈希）。
 */
public record PlatformRefreshToken(long id, long adminId, String tokenHash, Instant expiresAt,
                                   boolean revoked, Instant createdAt) {

    public PlatformRefreshToken {
        if (id <= 0 || adminId <= 0) {
            throw new IllegalArgumentException("refresh token ids must be positive");
        }
        if (tokenHash == null || tokenHash.length() != 64) {
            throw new IllegalArgumentException("invalid refresh token hash");
        }
        if (expiresAt == null || createdAt == null) {
            throw new IllegalArgumentException("expiresAt/createdAt must not be null");
        }
    }
}
