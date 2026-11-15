package com.veryal.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级限流：由 RateLimitAspect 在调用前消费令牌。
 *
 * <pre>
 * &#64;RateLimit(policy = "message-send", key = "#p0.tenantId() + ':' + #p0.senderId()")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** RateLimiterRegistry 中的策略名。 */
    String policy();

    /** 限流 key 的 SpEL；为空时按 dimension 推导。 */
    String key() default "";

    RateLimitDimension dimension() default RateLimitDimension.USER;
}
