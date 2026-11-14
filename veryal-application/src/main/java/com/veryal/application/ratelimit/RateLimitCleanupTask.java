package com.veryal.application.ratelimit;

import com.veryal.common.ratelimit.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 周期性清理限流器中的空闲 key，避免长期运行内存增长。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitCleanupTask {

    private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(10);

    private final RateLimiterRegistry rateLimiterRegistry;

    @Scheduled(fixedDelay = 60_000L)
    public void cleanup() {
        rateLimiterRegistry.evictIdle(IDLE_THRESHOLD);
        log.debug("rate limiter idle keys evicted");
    }
}
