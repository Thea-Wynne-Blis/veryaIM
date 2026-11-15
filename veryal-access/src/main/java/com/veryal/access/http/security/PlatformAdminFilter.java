package com.veryal.access.http.security;

import com.veryal.application.platform.PlatformAdminService;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.VeryalException;
import com.veryal.common.json.JsonUtils;
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
 * 平台管理接口认证：Bearer 平台 Token，与租户用户 Token 完全分离。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
@RequiredArgsConstructor
public class PlatformAdminFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = "veryal.platformPrincipal";
    private static final String LOGIN_PATH = "/api/v1/platform/auth/login";
    private static final String REFRESH_PATH = "/api/v1/platform/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/platform/auth/logout";
    private static final String BEARER_PREFIX = "Bearer ";

    private final PlatformAdminService platformAdminService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/platform/")) {
            return true;
        }
        return LOGIN_PATH.equals(path) || REFRESH_PATH.equals(path) || LOGOUT_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            writeError(response, ErrorCode.UNAUTHORIZED, "missing platform bearer token");
            return;
        }
        try {
            PlatformPrincipal principal = platformAdminService.authenticate(
                    authorization.substring(BEARER_PREFIX.length()).trim());
            request.setAttribute(ATTRIBUTE, principal);
            filterChain.doFilter(request, response);
        } catch (VeryalException e) {
            writeError(response, e.errorCode(), e.getMessage());
        } catch (RuntimeException e) {
            writeError(response, ErrorCode.INTERNAL_ERROR, "internal error");
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode, String message)
            throws IOException {
        response.setStatus(errorCode.httpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJson(ApiResponse.failure(errorCode, message)));
    }
}
