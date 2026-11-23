package com.veryal.access.http.security;

import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.AuthApplicationService;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.VeryalException;
import com.veryal.common.json.JsonUtils;
import com.veryal.common.observability.LogFields;
import com.veryal.common.observability.TraceContext;
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
 * HTTP 认证与链路上下文过滤器。
 *
 * <p>统一生成 / 透传 traceId，校验 Bearer Token 并把当前用户放入请求属性。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = "veryal.authenticatedUser";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthApplicationService authApplicationService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1")) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (path.startsWith("/api/v1/platform/")) {
            return true;
        }
        // 头像公开只读：<img src> 无法携带 Authorization 头。
        if (path.startsWith("/api/v1/avatars/")) {
            return true;
        }
        return path.equals("/api/v1/auth/register") || path.equals("/api/v1/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader("X-Request-Id");
        try (TraceContext.Scope ignored = TraceContext.start(traceId)) {
            TraceContext.put(LogFields.REQUEST_ID, request.getHeader("X-Request-Id"));
            response.setHeader("X-Trace-Id", TraceContext.currentTraceId());
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
                writeError(response, ErrorCode.UNAUTHORIZED, "missing bearer token");
                return;
            }
            try {
                AuthenticatedUser user = authApplicationService.authenticate(
                        authorization.substring(BEARER_PREFIX.length()).trim());
                request.setAttribute(USER_ATTRIBUTE, user);
                TraceContext.put(LogFields.USER_ID, Long.toString(user.userId()));
                TraceContext.put(LogFields.DEVICE_ID, user.deviceId());
                filterChain.doFilter(request, response);
            } catch (AuthenticationException e) {
                writeError(response, e.errorCode(), e.getMessage());
            } catch (VeryalException e) {
                writeError(response, e.errorCode(), e.getMessage());
            } catch (RuntimeException e) {
                writeError(response, ErrorCode.INTERNAL_ERROR, "internal error");
            } finally {
                TraceContext.put(LogFields.USER_ID, null);
                TraceContext.put(LogFields.DEVICE_ID, null);
                TraceContext.put(LogFields.REQUEST_ID, null);
            }
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.httpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJson(ApiResponse.failure(errorCode, message)));
    }
}
