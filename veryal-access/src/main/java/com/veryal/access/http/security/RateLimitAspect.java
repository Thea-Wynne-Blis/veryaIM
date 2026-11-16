package com.veryal.access.http.security;

import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.ratelimit.RateLimitProperties;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.ratelimit.RateLimit;
import com.veryal.common.ratelimit.RateLimitDimension;
import com.veryal.common.ratelimit.RateLimitResult;
import com.veryal.common.ratelimit.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 注解式限流切面：@RateLimit 方法调用前消费令牌，超限抛 429。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RateLimitProperties rateLimitProperties;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        if (!rateLimitProperties.isEnabled()) {
            return joinPoint.proceed();
        }
        String key = resolveKey(joinPoint, rateLimit);
        RateLimitResult result = rateLimiterRegistry.tryAcquire(rateLimit.policy(), key);
        if (!result.allowed()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "rate limit exceeded for " + rateLimit.policy() + ", retry after "
                            + result.retryAfterSeconds() + "s");
        }
        return joinPoint.proceed();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (rateLimit.key() != null && !rateLimit.key().isBlank()) {
            return evaluate(joinPoint, rateLimit.key());
        }
        return switch (rateLimit.dimension()) {
            case GLOBAL -> "global";
            case IP -> "ip:" + clientIp();
            case USER -> {
                AuthenticatedUser user = findUser(joinPoint.getArgs());
                yield user == null ? "ip:" + clientIp() : "user:" + user.userId();
            }
            case CUSTOM -> throw new IllegalStateException(
                    "@RateLimit(key=...) is required for CUSTOM dimension");
        };
    }

    private String evaluate(ProceedingJoinPoint joinPoint, String expression) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        String[] names = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            if (names != null && i < names.length) {
                context.setVariable(names[i], args[i]);
            }
        }
        Object value = PARSER.parseExpression(expression).getValue(context);
        return value == null ? "null" : value.toString();
    }

    private AuthenticatedUser findUser(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof AuthenticatedUser user) {
                return user;
            }
        }
        return null;
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "unknown";
    }
}
