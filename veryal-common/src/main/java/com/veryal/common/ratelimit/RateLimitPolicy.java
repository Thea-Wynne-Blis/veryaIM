package com.veryal.common.ratelimit;

import java.time.Duration;

/**
 * 单条限流策略。
 *
 * @param name            策略名，如 message-send
 * @param algorithm       选择具体限流算法（策略模式）
 * @param permits         令牌桶容量 / 窗口内允许次数
 * @param window          滑动窗口或固定窗口时长
 * @param refillPerSecond 令牌桶每秒补充令牌数；窗口算法忽略
 */
public record RateLimitPolicy(String name, RateLimitAlgorithm algorithm, int permits,
                              Duration window, double refillPerSecond) {

    public RateLimitPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("rate limit policy name must not be blank");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("rate limit algorithm must not be null");
        }
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofSeconds(1);
        }
        if (refillPerSecond < 0) {
            throw new IllegalArgumentException("refillPerSecond must not be negative");
        }
    }

    public static RateLimitPolicy tokenBucket(String name, int capacity, double refillPerSecond) {
        return new RateLimitPolicy(name, RateLimitAlgorithm.TOKEN_BUCKET, capacity,
                Duration.ofSeconds(1), refillPerSecond);
    }

    public static RateLimitPolicy slidingWindow(String name, int permits, Duration window) {
        return new RateLimitPolicy(name, RateLimitAlgorithm.SLIDING_WINDOW, permits, window, 0);
    }

    public static RateLimitPolicy fixedWindow(String name, int permits, Duration window) {
        return new RateLimitPolicy(name, RateLimitAlgorithm.FIXED_WINDOW, permits, window, 0);
    }
}
