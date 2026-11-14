package com.veryal.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterRegistryTest {

    @Test
    void selectsImplementationByAlgorithm() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiterRegistry registry = new RateLimiterRegistry(List.of(
                RateLimitPolicy.tokenBucket("token", 1, 1),
                RateLimitPolicy.slidingWindow("window", 1, Duration.ofSeconds(60))
        ), clock);

        assertThat(registry.policyNames()).containsExactlyInAnyOrder("token", "window");
        assertThat(registry.tryAcquire("token", "k").allowed()).isTrue();
        assertThat(registry.tryAcquire("token", "k").allowed()).isFalse();
        assertThat(registry.tryAcquire("window", "k").allowed()).isTrue();
        assertThat(registry.tryAcquire("window", "k").allowed()).isFalse();
    }

    @Test
    void rejectsUnknownPolicyAndDuplicateNames() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiterRegistry registry = new RateLimiterRegistry(List.of(
                RateLimitPolicy.tokenBucket("token", 1, 1)), clock);
        assertThatThrownBy(() -> registry.tryAcquire("missing", "k"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RateLimiterRegistry(List.of(
                RateLimitPolicy.tokenBucket("dup", 1, 1),
                RateLimitPolicy.slidingWindow("dup", 1, Duration.ofSeconds(10))), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evictsIdleKeys() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiterRegistry registry = new RateLimiterRegistry(List.of(
                RateLimitPolicy.tokenBucket("token", 1, 1)), clock);
        assertThat(registry.tryAcquire("token", "k").allowed()).isTrue();

        clock.advance(Duration.ofMinutes(30));
        registry.evictIdle(Duration.ofMinutes(10));
        assertThat(registry.tryAcquire("token", "k").allowed()).isTrue();
    }
}
