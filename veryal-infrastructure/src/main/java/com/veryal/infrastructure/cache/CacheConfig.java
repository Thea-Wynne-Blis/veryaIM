package com.veryal.infrastructure.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 本地缓存装配。
 *
 * <p>CacheManager 由 Spring Boot Caffeine 自动配置提供（见 {@code spring.cache.*}），
 * 本模块只负责显式开启缓存注解支持，避免缓存行为隐式生效。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
