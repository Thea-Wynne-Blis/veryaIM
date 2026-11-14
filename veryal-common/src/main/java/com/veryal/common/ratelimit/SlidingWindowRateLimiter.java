package com.veryal.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 滑动窗口限流：记录窗口内的请求时间戳，精确控制任意时间窗口内的请求数。
 */
public final class SlidingWindowRateLimiter implements RateLimiter {

    private final RateLimitPolicy policy;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(RateLimitPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        long now = clock.millis();
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
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

        private final Deque<Long> timestamps = new ArrayDeque<>();
        private long lastAccess;

        private synchronized RateLimitResult acquire(long now, RateLimitPolicy policy) {
            long windowMillis = policy.window().toMillis();
            lastAccess = now;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() < policy.permits()) {
                timestamps.addLast(now);
                return RateLimitResult.allowed(policy.permits() - timestamps.size());
            }
            long retryAfterMillis = timestamps.peekFirst() + windowMillis - now;
            return RateLimitResult.denied(Math.max(1, (retryAfterMillis + 999) / 1000));
        }
    }
}
