package com.veryal.common.ratelimit;

/**
 * 限流结果。
 *
 * @param allowed           是否放行
 * @param remaining         剩余可用额度（窗口算法为剩余次数，令牌桶为剩余令牌数）
 * @param retryAfterSeconds 建议重试等待秒数；无法估算时为 -1
 */
public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {

    public static RateLimitResult allowed(long remaining) {
        return new RateLimitResult(true, Math.max(0, remaining), 0);
    }

    public static RateLimitResult denied(long retryAfterSeconds) {
        return new RateLimitResult(false, 0, retryAfterSeconds);
    }
}
