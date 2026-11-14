package com.veryal.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 固定窗口限流：按固定时间窗口计数，实现简单，窗口边界存在突发。
 */
public final class FixedWindowRateLimiter implements RateLimiter {

    private final RateLimitPolicy policy;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(RateLimitPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        long now = clock.millis();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        return window.acquire(now, policy);
    }

    @Override
    public void evictIdle(Instant now, Duration idle) {
        long threshold = now.toEpochMilli() - idle.toMillis();
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().lastAccess < threshold) {
                iterator.remove();
            }
        }
    }

    private static final class Window {

        private long windowStart;
        private int count;
        private long lastAccess;

        private Window(long now) {
            this.windowStart = now;
            this.lastAccess = now;
        }

        private synchronized RateLimitResult acquire(long now, RateLimitPolicy policy) {
            long windowMillis = policy.window().toMillis();
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                count = 0;
            }
            lastAccess = now;
            if (count < policy.permits()) {
                count++;
                return RateLimitResult.allowed(policy.permits() - count);
            }
            long retryAfterMillis = windowStart + windowMillis - now;
            return RateLimitResult.denied(Math.max(1, (retryAfterMillis + 999) / 1000));
        }
    }
}
