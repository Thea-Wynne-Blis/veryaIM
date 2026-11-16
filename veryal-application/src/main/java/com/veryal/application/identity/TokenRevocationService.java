package com.veryal.application.identity;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 吊销表。
 *
 * <p>JWT 本身无状态，退出登录时把该 Token 的 {@code jti} 记为已吊销，
 * 后续请求在 {@link AuthApplicationService#authenticate(String)} 中被拒绝。</p>
 *
 * <p><b>当前实现为单节点内存表</b>，多实例部署时需要替换为 Redis 集合
 * （键 {@code veryal:revoked:{jti}} + 过期时间），否则在 A 节点退出后
 * 请求落到 B 节点仍会被放行。</p>
 */
@Service
public class TokenRevocationService {

    /** 保留时间与 Token 最长有效期一致，过期条目自动失效。 */
    private static final Duration RETENTION = Duration.ofDays(7);

    /** 超过该数量时触发一次清理，避免长期运行内存膨胀。 */
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        revoked.put(tokenId, Instant.now().plus(RETENTION));
        if (revoked.size() > PRUNE_THRESHOLD) {
            prune();
        }
    }

    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        Instant until = revoked.get(tokenId);
        if (until == null) {
            return false;
        }
        if (until.isBefore(Instant.now())) {
            revoked.remove(tokenId);
            return false;
        }
        return true;
    }

    private void prune() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
