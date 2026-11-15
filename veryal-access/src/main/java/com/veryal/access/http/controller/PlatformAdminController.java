package com.veryal.access.http.controller;

import com.veryal.access.http.dto.ChangePlatformRoleRequest;
import com.veryal.access.http.dto.CreatePlatformAdminRequest;
import com.veryal.access.http.dto.PlatformAdminResponse;
import com.veryal.access.http.dto.ResetPlatformPasswordRequest;
import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.platform.PlatformAdminService;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.platform.PlatformAdminRole;
import com.veryal.domain.platform.PlatformAdminStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 平台管理员账号管理，仅 PLATFORM_ADMIN 可用。
 */
@RestController
@RequestMapping("/api/v1/platform/admins")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    @GetMapping
    public ApiResponse<List<PlatformAdminResponse>> list(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal) {
        return ApiResponse.ok(platformAdminService.listAdmins(principal).stream()
                .map(PlatformAdminResponse::from)
                .toList());
    }

    @PostMapping
    public ApiResponse<PlatformAdminResponse> create(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @Valid @RequestBody CreatePlatformAdminRequest request) {
        return ApiResponse.ok(PlatformAdminResponse.from(platformAdminService.createAdmin(principal,
                request.username(), request.password(), request.displayName(),
                parseRole(request.role()))));
    }

    @PostMapping("/{adminId}/disable")
    public ApiResponse<PlatformAdminResponse> disable(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long adminId) {
        return ApiResponse.ok(PlatformAdminResponse.from(platformAdminService.setStatus(principal,
                adminId, PlatformAdminStatus.DISABLED)));
    }

    @PostMapping("/{adminId}/enable")
    public ApiResponse<PlatformAdminResponse> enable(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long adminId) {
        return ApiResponse.ok(PlatformAdminResponse.from(platformAdminService.setStatus(principal,
                adminId, PlatformAdminStatus.ACTIVE)));
    }

    @PostMapping("/{adminId}/password")
    public ApiResponse<Void> resetPassword(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long adminId,
            @Valid @RequestBody ResetPlatformPasswordRequest request) {
        platformAdminService.resetPassword(principal, adminId, request.password());
        return ApiResponse.ok();
    }

    @PostMapping("/{adminId}/role")
    public ApiResponse<PlatformAdminResponse> changeRole(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long adminId,
            @Valid @RequestBody ChangePlatformRoleRequest request) {
        return ApiResponse.ok(PlatformAdminResponse.from(platformAdminService.changeRole(principal,
                adminId, parseRole(request.role()))));
    }

    private PlatformAdminRole parseRole(String role) {
        try {
            return PlatformAdminRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid platform role");
        }
    }
}
