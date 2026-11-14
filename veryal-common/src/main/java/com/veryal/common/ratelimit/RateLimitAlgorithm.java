package com.veryal.common.ratelimit;

/**
 * 限流算法。
 */
public enum RateLimitAlgorithm {

    TOKEN_BUCKET,
    SLIDING_WINDOW,
    FIXED_WINDOW
}
