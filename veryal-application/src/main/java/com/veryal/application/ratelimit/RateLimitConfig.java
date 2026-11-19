package com.veryal.application.ratelimit;

import com.veryal.common.ratelimit.RateLimitPolicy;
import com.veryal.common.ratelimit.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 限流装配：把配置的每条策略交给注册表，由算法枚举选择令牌桶/滑动窗口/固定窗口实现。
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(RateLimitProperties properties) {
        List<RateLimitPolicy> policies = new ArrayList<>();
        for (RateLimitProperties.Policy policy : properties.getPolicies()) {
            policies.add(new RateLimitPolicy(policy.getName(), policy.getAlgorithm(),
                    policy.getPermits(), Duration.ofSeconds(policy.getWindowSeconds()),
                    policy.getRefillPerSecond()));
        }
        if (policies.isEmpty()) {
            policies.addAll(defaultPolicies());
        }
        return new RateLimiterRegistry(policies);
    }

    private List<RateLimitPolicy> defaultPolicies() {
        return List.of(
                RateLimitPolicy.slidingWindow("http-login", 10, Duration.ofSeconds(60)),
                RateLimitPolicy.tokenBucket("http-api", 200, 50),
                RateLimitPolicy.slidingWindow("gateway-login", 10, Duration.ofSeconds(60)),
                RateLimitPolicy.tokenBucket("message-send", 30, 15),
                RateLimitPolicy.slidingWindow("moment-publish", 20, Duration.ofSeconds(60)),
                RateLimitPolicy.slidingWindow("moment-comment", 30, Duration.ofSeconds(60))
        );
    }
}
