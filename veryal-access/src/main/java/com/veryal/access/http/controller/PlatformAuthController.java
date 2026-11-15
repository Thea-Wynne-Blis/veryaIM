package com.veryal.access.http.controller;

import com.veryal.access.http.dto.PlatformAuthResponse;
import com.veryal.access.http.dto.PlatformLoginRequest;
import com.veryal.application.platform.PlatformAdminService;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台管理员登录：独立于租户用户登录。
 */
@RestController
@RequestMapping("/api/v1/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAdminService platformAdminService;

    @PostMapping("/login")
    public ApiResponse<PlatformAuthResponse> login(@Valid @RequestBody PlatformLoginRequest request) {
        return ApiResponse.ok(PlatformAuthResponse.from(
                platformAdminService.login(request.username(), request.password())));
    }
}
