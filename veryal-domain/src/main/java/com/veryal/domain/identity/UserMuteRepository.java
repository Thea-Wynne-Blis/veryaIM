package com.veryal.domain.identity;

import java.time.Instant;
import java.util.Optional;

/**
 * 用户全局禁言仓储。
 */
public interface UserMuteRepository {

    Optional<Instant> mutedUntil(long userId);

    void mute(long userId, Instant until, String reason, long operatorId, Instant now);

    void unmute(long userId);
}
