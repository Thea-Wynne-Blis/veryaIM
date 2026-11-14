package com.veryal.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 本地缓存装配。
 *
 * <p>缓存策略在代码中显式声明，不依赖 {@code spring.cache.*} 自动配置：
 * 便于逐项审查容量、过期时间与统计开关，也避免修改 YAML 时隐式改变缓存行为。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_CACHE = "user";

    private static final long USER_MAX_SIZE = 100_000;
    private static final Duration USER_TTL = Duration.ofMinutes(10);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(USER_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(USER_MAX_SIZE)
                .expireAfterWrite(USER_TTL)
                .recordStats());
        return manager;
    }
}
