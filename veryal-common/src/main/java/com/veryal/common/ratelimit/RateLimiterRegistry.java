package com.veryal.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 限流器注册表：按策略名选择具体算法实现（策略模式）。
 */
public final class RateLimiterRegistry {

    private final Map<String, RateLimiter> limiters;
    private final Clock clock;

    public RateLimiterRegistry(Collection<RateLimitPolicy> policies) {
        this(policies, Clock.systemUTC());
    }

    public RateLimiterRegistry(Collection<RateLimitPolicy> policies, Clock clock) {
        this.clock = clock;
        Map<String, RateLimiter> map = new LinkedHashMap<>();
        for (RateLimitPolicy policy : policies) {
            RateLimiter limiter = create(policy, clock);
            if (map.put(policy.name(), limiter) != null) {
                throw new IllegalArgumentException("duplicate rate limit policy: " + policy.name());
            }
        }
        this.limiters = Collections.unmodifiableMap(map);
    }

    public RateLimitResult tryAcquire(String policyName, String key) {
        RateLimiter limiter = limiters.get(policyName);
        if (limiter == null) {
            throw new IllegalArgumentException("unknown rate limit policy: " + policyName);
        }
        return limiter.tryAcquire(key);
    }

    public void evictIdle(Duration idle) {
        var now = clock.instant();
        limiters.values().forEach(limiter -> limiter.evictIdle(now, idle));
    }

    public Set<String> policyNames() {
        return limiters.keySet();
    }

    private static RateLimiter create(RateLimitPolicy policy, Clock clock) {
        return switch (policy.algorithm()) {
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(policy, clock);
            case SLIDING_WINDOW -> new SlidingWindowRateLimiter(policy, clock);
            case FIXED_WINDOW -> new FixedWindowRateLimiter(policy, clock);
        };
    }
}
