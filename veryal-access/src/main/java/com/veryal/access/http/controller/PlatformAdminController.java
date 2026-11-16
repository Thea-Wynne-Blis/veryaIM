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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 平台管理员账号管理，仅 PLATFORM_ADMIN 可用。
 */
@Tag(name = "运营-管理员")
@RestController
@RequestMapping("/api/v1/platform/admins")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    @Operation(summary = "管理员列表", description = "返回全部运营账号及其状态。")
    @GetMapping
    public ApiResponse<List<PlatformAdminResponse>> list(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal) {
        return ApiResponse.ok(platformAdminService.listAdmins(principal).stream()
                .map(PlatformAdminResponse::from)
                .toList());
    }

    @Operation(summary = "创建运营管理员", description = "指定用户名、初始密码与角色。")
    @PostMapping
    public ApiResponse<PlatformAdminResponse> create(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @Valid @RequestBody CreatePlatformAdminRequest request) {
        return ApiResponse.ok(PlatformAdminResponse.from(platformAdminService.createAdmin(principal,
                request.username(), request.password(), request.displayName(),
                parseRole(request.role()))));
    }

    @Operation(summary = "停用管理员", description = "停用后该账号立即无法登录。")
    @PostMapping("/{adminId}/disable")
    public ApiResponse<PlatformAdminResponse> disable(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long adminId) {
        return ApiResponse.ok(PlatformAdminResponse.from(platformAdminService.setStatus(principal,
                adminId, PlatformAdminStatus.DISABLED)));
    }

    @Operation(summary = "启用管理员", description = "恢复被停用账号的登录权限。")
    @PostMapping("/{adminId}/enable")
    public ApiResponse<PlatformAdminResponse> enable(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long adminId) {
        return ApiResponse.ok(PlatformAdminResponse.from(platformAdminService.setStatus(principal,
                adminId, PlatformAdminStatus.ACTIVE)));
    }

    @Operation(summary = "重置管理员密码", description = "由上级管理员重置，无需旧密码。")
    @PostMapping("/{adminId}/password")
    public ApiResponse<Void> resetPassword(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long adminId,
            @Valid @RequestBody ResetPlatformPasswordRequest request) {
        platformAdminService.resetPassword(principal, adminId, request.password());
        return ApiResponse.ok();
    }

    @Operation(summary = "调整管理员角色", description = "变更权限范围。")
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
