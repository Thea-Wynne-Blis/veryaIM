package com.veryal.access.http.controller;

import com.veryal.access.http.dto.OpsActionRequest;
import com.veryal.access.http.dto.OpsMuteRequest;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 运营后台用户管理。
 */
@Tag(name = "运营-用户")
@RestController
@RequestMapping("/api/v1/platform/ops/users")
@RequiredArgsConstructor
public class OpsUserController {

    private final OpsUserService opsUserService;
    private final OpsPermission opsPermission;

    @Operation(summary = "分页查询用户", description = "支持按关键词、状态过滤。")
    @GetMapping
    public ApiResponse<List<OpsUserService.OpsUserView>> search(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsUserService.search(keyword, limit));
    }

    @Operation(summary = "用户详情", description = "返回资料、状态与近期活跃情况。")
    @GetMapping("/{userId}")
    public ApiResponse<OpsUserService.OpsUserView> get(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long userId) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsUserService.get(userId));
    }

    @Operation(summary = "封禁用户", description = "封禁后无法登录与发消息。")
    @PostMapping("/{userId}/ban")
    public ApiResponse<OpsUserService.OpsUserView> ban(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long userId, @RequestBody(required = false) OpsActionRequest request) {
        opsPermission.requireWrite(principal);
        return ApiResponse.ok(opsUserService.setStatus(principal.adminId(), userId,
                UserStatus.DISABLED, request == null ? null : request.reason()));
    }

    @Operation(summary = "解封用户", description = "恢复账号正常使用。")
    @PostMapping("/{userId}/unban")
    public ApiResponse<OpsUserService.OpsUserView> unban(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long userId, @RequestBody(required = false) OpsActionRequest request) {
        opsPermission.requireWrite(principal);
        return ApiResponse.ok(opsUserService.setStatus(principal.adminId(), userId,
                UserStatus.ACTIVE, request == null ? null : request.reason()));
    }

    @Operation(summary = "全局禁言用户", description = "禁言期间只能读不能发，时长单位分钟。")
    @PostMapping("/{userId}/mute")
    public ApiResponse<OpsUserService.MuteView> mute(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long userId, @RequestBody OpsMuteRequest request) {
        opsPermission.requireWrite(principal);
        java.time.Instant until = opsUserService.mute(principal.adminId(), userId,
                request.minutes() == null ? 60 : request.minutes(), request.reason());
        return ApiResponse.ok(new OpsUserService.MuteView(userId, until));
    }

    @Operation(summary = "解除全局禁言", description = "立即恢复发言能力。")
    @PostMapping("/{userId}/unmute")
    public ApiResponse<Void> unmute(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long userId) {
        opsPermission.requireWrite(principal);
        opsUserService.unmute(principal.adminId(), userId);
        return ApiResponse.ok();
    }
}
