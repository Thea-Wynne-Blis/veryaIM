package com.veryal.access.http.security;

import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.ratelimit.RateLimitProperties;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.common.ratelimit.RateLimitResult;
import com.veryal.common.ratelimit.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 限流过滤器。
 *
 * <p>登录接口按客户端 IP 使用滑动窗口；其它 API 按用户（未认证时按 IP）使用令牌桶。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String API_POLICY = "http-api";
    /**
     * 头像 / 表情预览都由 {@code <img>} 直接加载，不带 Authorization，只能按 IP 计数；
     * 一屏几十张图会把公共出口 IP 的额度吃光，因此各给一套更宽松的独立令牌桶，
     * 但仍然限速——不能因为「是图片」就完全不设防。
     */
    private static final String AVATAR_PREFIX = "/api/v1/avatars/";
    private static final String AVATAR_POLICY = "avatar-image";
    private static final String STICKER_IMAGE_PREFIX = "/api/v1/stickers/image";
    private static final String STICKER_IMAGE_POLICY = "sticker-image";

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RateLimitProperties rateLimitProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !rateLimitProperties.isEnabled()
                || !request.getRequestURI().startsWith("/api/v1");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (LOGIN_PATH.equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientIp = ClientAddress.of(request);
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(
                AuthenticationFilter.USER_ATTRIBUTE);
        String key = user == null ? "ip:" + clientIp : "user:" + user.userId();
        String policy = path.startsWith(AVATAR_PREFIX) ? AVATAR_POLICY
                : path.startsWith(STICKER_IMAGE_PREFIX) ? STICKER_IMAGE_POLICY
                : API_POLICY;

        RateLimitResult result = rateLimiterRegistry.tryAcquire(policy, key);
        response.setHeader("X-RateLimit-Remaining", Long.toString(result.remaining()));
        if (!result.allowed()) {
            if (result.retryAfterSeconds() > 0) {
                response.setHeader("Retry-After", Long.toString(result.retryAfterSeconds()));
            }
            response.setStatus(ErrorCode.RATE_LIMITED.httpStatus());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(JsonUtils.toJson(ApiResponse.failure(ErrorCode.RATE_LIMITED,
                    "rate limit exceeded, retry after " + result.retryAfterSeconds() + "s")));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
