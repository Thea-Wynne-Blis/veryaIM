package com.veryal.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    @Test
    void resetsAtFixedWindowBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(
                RateLimitPolicy.fixedWindow("test", 2, Duration.ofSeconds(60)), clock);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(60));
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    }
}
