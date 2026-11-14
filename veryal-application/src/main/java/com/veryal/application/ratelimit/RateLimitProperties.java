package com.veryal.application.ratelimit;

import com.veryal.common.ratelimit.RateLimitAlgorithm;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 限流策略配置。每条策略可选择不同算法，运行时由策略模式选择实现。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.rate-limit")
public class RateLimitProperties {

    private List<Policy> policies = new ArrayList<>();

    @Getter
    @Setter
    public static class Policy {

        private String name;
        private RateLimitAlgorithm algorithm = RateLimitAlgorithm.TOKEN_BUCKET;
        private int permits = 100;
        private long windowSeconds = 60;
        private double refillPerSecond;
    }
}
