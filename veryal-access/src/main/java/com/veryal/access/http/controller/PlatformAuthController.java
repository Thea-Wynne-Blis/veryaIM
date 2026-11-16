package com.veryal.access.http.controller;

import com.veryal.access.http.dto.MfaCodeRequest;
import com.veryal.access.http.dto.MfaSetupResponse;
import com.veryal.access.http.dto.PlatformAuthResponse;
import com.veryal.access.http.dto.PlatformLoginRequest;
import com.veryal.access.http.dto.PlatformRefreshRequest;
import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.platform.PlatformAdminService;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 平台管理员登录、刷新、登出与 MFA。
 */
@Tag(name = "运营认证")
@RestController
@RequestMapping("/api/v1/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAdminService platformAdminService;

    @Operation(summary = "运营后台登录", description = "管理员专用登录入口，账号体系与普通用户完全隔离。开启 MFA 后需在第二步提交动态码。")
    @PostMapping("/login")
    public ApiResponse<PlatformAuthResponse> login(@Valid @RequestBody PlatformLoginRequest request) {
        return ApiResponse.ok(PlatformAuthResponse.from(platformAdminService.login(
                request.username(), request.password(), request.mfaCode())));
    }

    @Operation(summary = "刷新运营令牌", description = "用 refresh token 换取新的访问令牌。")
    @PostMapping("/refresh")
    public ApiResponse<PlatformAuthResponse> refresh(
            @Valid @RequestBody PlatformRefreshRequest request) {
        return ApiResponse.ok(PlatformAuthResponse.from(
                platformAdminService.refresh(request.refreshToken())));
    }

    @Operation(summary = "运营登出", description = "使当前会话的令牌失效。")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody PlatformRefreshRequest request) {
        platformAdminService.logout(request.refreshToken());
        return ApiResponse.ok();
    }

    @Operation(summary = "初始化 MFA", description = "生成 TOTP 密钥与二维码地址，需随后调用 enable 才会真正生效。")
    @PostMapping("/mfa/setup")
    public ApiResponse<MfaSetupResponse> setupMfa(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal) {
        return ApiResponse.ok(MfaSetupResponse.from(platformAdminService.setupMfa(principal)));
    }

    @Operation(summary = "启用 MFA", description = "提交一次动态码校验通过后开启双因素。")
    @PostMapping("/mfa/enable")
    public ApiResponse<Void> enableMfa(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @Valid @RequestBody MfaCodeRequest request) {
        platformAdminService.enableMfa(principal, request.code());
        return ApiResponse.ok();
    }

    @Operation(summary = "关闭 MFA", description = "关闭双因素认证。")
    @PostMapping("/mfa/disable")
    public ApiResponse<Void> disableMfa(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @Valid @RequestBody MfaCodeRequest request) {
        platformAdminService.disableMfa(principal, request.code());
        return ApiResponse.ok();
    }
}
