package com.veryal.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    @Test
    void allowsBurstThenRefillsOverTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimitPolicy policy = RateLimitPolicy.tokenBucket("test", 2, 1);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(policy, clock);

        assertThat(limiter.tryAcquire("user:1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user:1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user:1").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("user:1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user:1").allowed()).isFalse();
    }

    @Test
    void keysAreIsolated() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(
                RateLimitPolicy.tokenBucket("test", 1, 1), clock);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void noRefillDeniesUntilWindowResetIsNotApplicable() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(
                RateLimitPolicy.tokenBucket("test", 1, 0), clock);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        RateLimitResult denied = limiter.tryAcquire("a");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }
}
