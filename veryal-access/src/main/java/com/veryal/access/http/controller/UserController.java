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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户资料与设备接口。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;
    private final AuditApplicationService auditApplicationService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(UserResponse.from(userApplicationService.getProfile(user.userId())));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> update(@CurrentUser AuthenticatedUser user,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(UserResponse.from(
                userApplicationService.updateProfile(user.userId(), request.nickname(), request.avatar())));
    }

    @GetMapping("/me/devices")
    public ApiResponse<List<DeviceResponse>> devices(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(userApplicationService.listDevices(user.userId()).stream()
                .map(DeviceResponse::from)
                .toList());
    }

    @GetMapping("/me/audit-logs")
    public ApiResponse<List<AuditLogResponse>> auditLogs(@CurrentUser AuthenticatedUser user,
                                                         @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(auditApplicationService.listMine(user.tenantId(), user.userId(), limit)
                .stream()
                .map(AuditLogResponse::from)
                .toList());
    }
}
