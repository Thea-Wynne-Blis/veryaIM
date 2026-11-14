package com.veryal.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 令牌桶限流：按 key 维护令牌，支持突发流量与恒定补充速率。
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private final RateLimitPolicy policy;
    private final Clock clock;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(RateLimitPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(policy.permits(), now));
        return bucket.acquire(now, policy);
    }

    @Override
    public void evictIdle(Instant now, Duration idle) {
        long threshold = now.toEpochMilli() - idle.toMillis();
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().lastAccess < threshold) {
                iterator.remove();
            }
        }
    }

    private static final class Bucket {

        private double tokens;
        private long lastRefillMillis;
        private long lastAccess;

        private Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefillMillis = now;
            this.lastAccess = now;
        }

        private synchronized RateLimitResult acquire(long now, RateLimitPolicy policy) {
            double elapsedSeconds = Math.max(0, now - lastRefillMillis) / 1000.0;
            if (policy.refillPerSecond() > 0 && elapsedSeconds > 0) {
                tokens = Math.min(policy.permits(), tokens + elapsedSeconds * policy.refillPerSecond());
                lastRefillMillis = now;
            }
            lastAccess = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return RateLimitResult.allowed((long) Math.floor(tokens));
            }
            long retryAfter = policy.refillPerSecond() > 0
                    ? (long) Math.ceil((1.0 - tokens) / policy.refillPerSecond())
                    : -1;
            return RateLimitResult.denied(Math.max(1, retryAfter));
        }
    }
}
