package com.veryal.access.http.controller;

import com.veryal.access.http.dto.OpsActionRequest;
import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.ops.OpsPermission;
import com.veryal.application.ops.OpsUserService;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.core.ApiResponse;
import com.veryal.domain.identity.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营后台用户管理。
 */
@RestController
@RequestMapping("/api/v1/platform/ops/users")
@RequiredArgsConstructor
public class OpsUserController {

    private final OpsUserService opsUserService;
    private final OpsPermission opsPermission;

    @GetMapping
    public ApiResponse<List<OpsUserService.OpsUserView>> search(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsUserService.search(keyword, limit));
    }

    @GetMapping("/{userId}")
    public ApiResponse<OpsUserService.OpsUserView> get(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long userId) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsUserService.get(userId));
    }

    @PostMapping("/{userId}/ban")
    public ApiResponse<OpsUserService.OpsUserView> ban(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long userId, @RequestBody(required = false) OpsActionRequest request) {
        opsPermission.requireWrite(principal);
        return ApiResponse.ok(opsUserService.setStatus(principal.adminId(), userId,
                UserStatus.DISABLED, request == null ? null : request.reason()));
    }

    @PostMapping("/{userId}/unban")
    public ApiResponse<OpsUserService.OpsUserView> unban(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long userId, @RequestBody(required = false) OpsActionRequest request) {
        opsPermission.requireWrite(principal);
        return ApiResponse.ok(opsUserService.setStatus(principal.adminId(), userId,
                UserStatus.ACTIVE, request == null ? null : request.reason()));
    }
}
