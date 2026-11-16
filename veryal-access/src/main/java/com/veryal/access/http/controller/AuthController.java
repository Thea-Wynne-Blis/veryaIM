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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 认证接口。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final UserApplicationService userApplicationService;

    @Operation(summary = "注册用户", description = "创建账号并返回用户信息。用户名唯一，密码要求至少 6 位。注册后需调用登录接口获取 Token。")
    @PostMapping("/register")
    @RateLimit(policy = "http-login", dimension = RateLimitDimension.IP)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = UserResponse.from(authApplicationService.register(
                new RegisterCommand(request.username(), request.password(), request.nickname())));
        return ApiResponse.ok(user);
    }

    @Operation(summary = "登录并获取 Token", description = "返回 JWT 访问令牌与用户信息。`deviceId` + `platform` 用于多端会话管理，同一 deviceId 重复登录以最后一次为准。")
    @PostMapping("/login")
    @RateLimit(policy = "http-login", dimension = RateLimitDimension.IP)
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authApplicationService.login(new LoginCommand(request.username(), request.password(), request.deviceId(),
                        request.platform(), request.deviceName()));
        return ApiResponse.ok(AuthResponse.from(result));
    }

    @Operation(summary = "查询当前登录用户", description = "根据请求头 `Authorization: Bearer <token>` 解析出当前用户，用于前端刷新页面后恢复登录态。")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(UserResponse.from(userApplicationService.getProfile(user.userId())));
    }
}
