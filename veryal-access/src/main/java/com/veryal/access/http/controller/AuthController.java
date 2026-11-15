package com.veryal.access.http.controller;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.LoginRequest;
import com.veryal.access.http.dto.RegisterRequest;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthApplicationService;
import com.veryal.application.identity.AuthResult;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.LoginCommand;
import com.veryal.application.identity.RegisterCommand;
import com.veryal.application.identity.UserApplicationService;
import com.veryal.application.tenant.TenantApplicationService;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.ratelimit.RateLimit;
import com.veryal.common.ratelimit.RateLimitDimension;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final UserApplicationService userApplicationService;
    private final TenantApplicationService tenantApplicationService;

    @PostMapping("/register")
    @RateLimit(policy = "http-login", dimension = RateLimitDimension.IP)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        long tenantId = tenantApplicationService.resolveActive(request.tenantCode()).id();
        UserResponse user = UserResponse.from(authApplicationService.register(tenantId,
                new RegisterCommand(request.username(), request.password(), request.nickname())));
        return ApiResponse.ok(user);
    }

    @PostMapping("/login")
    @RateLimit(policy = "http-login", dimension = RateLimitDimension.IP)
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        long tenantId = tenantApplicationService.resolveActive(request.tenantCode()).id();
        AuthResult result = authApplicationService.login(tenantId,
                new LoginCommand(request.username(), request.password(), request.deviceId(),
                        request.platform(), request.deviceName()));
        return ApiResponse.ok(AuthResponse.from(result));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(UserResponse.from(userApplicationService.getProfile(user.userId())));
    }
}
