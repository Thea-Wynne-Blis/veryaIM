package com.veryal.domain.platform;

import java.util.Optional;

/**
 * 平台 Refresh Token 仓储。
 */
public interface PlatformRefreshTokenRepository {

    void save(PlatformRefreshToken token);

    Optional<PlatformRefreshToken> findByHash(String tokenHash);

    void revoke(long tokenId);
}
