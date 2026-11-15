package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.platform.PlatformRefreshToken;
import com.veryal.domain.platform.PlatformRefreshTokenRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.PlatformRefreshTokenMapper;
import com.veryal.infrastructure.mysql.po.PlatformRefreshTokenPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 平台 Refresh Token MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisPlatformRefreshTokenRepository implements PlatformRefreshTokenRepository {

    private final PlatformRefreshTokenMapper refreshTokenMapper;

    @Override
    public void save(PlatformRefreshToken token) {
        PlatformRefreshTokenPO po = new PlatformRefreshTokenPO();
        po.id = token.id();
        po.adminId = token.adminId();
        po.tokenHash = token.tokenHash();
        po.expiresAt = SqlTime.toLocal(token.expiresAt());
        po.revoked = token.revoked() ? 1 : 0;
        po.createdAt = SqlTime.toLocal(token.createdAt());
        refreshTokenMapper.insert(po);
    }

    @Override
    public Optional<PlatformRefreshToken> findByHash(String tokenHash) {
        PlatformRefreshTokenPO po = refreshTokenMapper.selectOne(
                Wrappers.<PlatformRefreshTokenPO>lambdaQuery()
                        .eq(PlatformRefreshTokenPO::getTokenHash, tokenHash));
        return Optional.ofNullable(po).map(item -> new PlatformRefreshToken(item.id, item.adminId,
                item.tokenHash, SqlTime.toInstant(item.expiresAt), item.revoked == 1,
                SqlTime.toInstant(item.createdAt)));
    }

    @Override
    public void revoke(long tokenId) {
        refreshTokenMapper.update(null, Wrappers.<PlatformRefreshTokenPO>lambdaUpdate()
                .eq(PlatformRefreshTokenPO::getId, tokenId)
                .set(PlatformRefreshTokenPO::getRevoked, 1));
    }
}
