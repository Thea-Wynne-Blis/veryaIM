package com.veryal.common.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * 限流器策略接口。
 */
public interface RateLimiter {

    RateLimitResult tryAcquire(String key);

    /**
     * 清理长时间未访问的 key，避免内存无界增长。
     */
    void evictIdle(Instant now, Duration idle);
}
