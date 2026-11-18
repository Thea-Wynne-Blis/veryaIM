package com.veryal.access.http.controller;

import com.veryal.access.http.dto.AuditLogResponse;
import com.veryal.access.http.dto.DeviceResponse;
import com.veryal.access.http.dto.UpdateProfileRequest;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.audit.AuditApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.UserApplicationService;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 用户资料与设备接口。
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;
    private final AuditApplicationService auditApplicationService;

    @Operation(summary = "我的资料", description = "返回当前用户的昵称、头像、创建时间等基础资料。")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(UserResponse.from(userApplicationService.getProfile(user.userId())));
    }

    @Operation(summary = "修改我的资料", description = "可修改昵称、头像地址、所在地区与个性签名；地区/签名传空串表示清空。")
    @PutMapping("/me")
    public ApiResponse<UserResponse> update(@CurrentUser AuthenticatedUser user,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(UserResponse.from(userApplicationService.updateProfile(
                user.userId(), request.nickname(), request.avatar(),
                request.region(), request.signature())));
    }

    @Operation(summary = "我的登录设备", description = "列出该账号登录过的设备，含设备类型与最近活跃时间。"
            + "同平台同名的历史记录会合并为一条，当前登录设备排在最前。")
    @GetMapping("/me/devices")
    public ApiResponse<List<DeviceResponse>> devices(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(userApplicationService.listDevices(user.userId(), user.deviceId()).stream()
                .map(DeviceResponse::from)
                .toList());
    }

    @Operation(summary = "移除登录设备", description = "移除该设备，并清理同平台同名的历史记录；当前设备不可移除。返回清理的条数。")
    @DeleteMapping("/me/devices/{deviceId}")
    public ApiResponse<Integer> removeDevice(@CurrentUser AuthenticatedUser user,
                                             @PathVariable String deviceId) {
        return ApiResponse.ok(userApplicationService.removeDevice(user.userId(), user.deviceId(), deviceId));
    }

    @Operation(summary = "我的操作记录", description = "账号维度的安全与操作审计，按时间倒序。")
    @GetMapping("/me/audit-logs")
    public ApiResponse<List<AuditLogResponse>> auditLogs(@CurrentUser AuthenticatedUser user,
                                                         @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(auditApplicationService.listMine(user.userId(), limit)
                .stream()
                .map(AuditLogResponse::from)
                .toList());
    }
}
