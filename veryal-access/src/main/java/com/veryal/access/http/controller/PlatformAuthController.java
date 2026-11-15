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

/**
 * 平台管理员登录、刷新、登出与 MFA。
 */
@RestController
@RequestMapping("/api/v1/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAdminService platformAdminService;

    @PostMapping("/login")
    public ApiResponse<PlatformAuthResponse> login(@Valid @RequestBody PlatformLoginRequest request) {
        return ApiResponse.ok(PlatformAuthResponse.from(platformAdminService.login(
                request.username(), request.password(), request.mfaCode())));
    }

    @PostMapping("/refresh")
    public ApiResponse<PlatformAuthResponse> refresh(
            @Valid @RequestBody PlatformRefreshRequest request) {
        return ApiResponse.ok(PlatformAuthResponse.from(
                platformAdminService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody PlatformRefreshRequest request) {
        platformAdminService.logout(request.refreshToken());
        return ApiResponse.ok();
    }

    @PostMapping("/mfa/setup")
    public ApiResponse<MfaSetupResponse> setupMfa(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal) {
        return ApiResponse.ok(MfaSetupResponse.from(platformAdminService.setupMfa(principal)));
    }

    @PostMapping("/mfa/enable")
    public ApiResponse<Void> enableMfa(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @Valid @RequestBody MfaCodeRequest request) {
        platformAdminService.enableMfa(principal, request.code());
        return ApiResponse.ok();
    }

    @PostMapping("/mfa/disable")
    public ApiResponse<Void> disableMfa(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @Valid @RequestBody MfaCodeRequest request) {
        platformAdminService.disableMfa(principal, request.code());
        return ApiResponse.ok();
    }
}
