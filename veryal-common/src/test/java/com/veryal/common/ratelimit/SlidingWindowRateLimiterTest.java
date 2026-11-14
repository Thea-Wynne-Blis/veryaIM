package com.veryal.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {

    @Test
    void limitsWithinWindowAndAllowsAfterWindowSlides() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimitPolicy policy = RateLimitPolicy.slidingWindow("test", 2, Duration.ofSeconds(60));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(policy, clock);

        assertThat(limiter.tryAcquire("ip:1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip:1").allowed()).isTrue();
        RateLimitResult denied = limiter.tryAcquire("ip:1");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 60L);

        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire("ip:1").allowed()).isTrue();
    }

    @Test
    void windowIsPerKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                RateLimitPolicy.slidingWindow("test", 1, Duration.ofSeconds(10)), clock);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }
}
