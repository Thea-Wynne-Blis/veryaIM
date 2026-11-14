package com.veryal.bootstrap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.veryal.infrastructure.cache.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void configuresUserCacheInCode() {
        CacheManager cacheManager = new CacheConfig().cacheManager();

        var cache = cacheManager.getCache(CacheConfig.USER_CACHE);
        assertThat(cache).isInstanceOf(CaffeineCache.class);

        @SuppressWarnings("unchecked")
        Cache<Object, Object> nativeCache = (Cache<Object, Object>) ((CaffeineCache) cache).getNativeCache();
        Policy<Object, Object> policy = nativeCache.policy();
        assertThat(policy.eviction().orElseThrow().getMaximum()).isEqualTo(100_000L);
        assertThat(policy.expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofMinutes(10));
    }
}
