package com.veryal.access.http.controller;

import com.veryal.access.http.dto.OpsActionRequest;
import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.ops.OpsGroupService;
import com.veryal.application.ops.OpsPermission;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.core.ApiResponse;
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
 * 运营后台群管理。
 */
@Tag(name = "运营-群组")
@RestController
@RequestMapping("/api/v1/platform/ops/groups")
@RequiredArgsConstructor
public class OpsGroupController {

    private final OpsGroupService opsGroupService;
    private final OpsPermission opsPermission;

    @Operation(summary = "分页查询群组", description = "支持按群名搜索。")
    @GetMapping
    public ApiResponse<List<OpsGroupService.OpsGroupView>> search(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsGroupService.search(keyword, limit));
    }

    @Operation(summary = "群组详情", description = "返回群主、成员数与创建时间。")
    @GetMapping("/{groupId}")
    public ApiResponse<OpsGroupService.OpsGroupDetail> detail(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable String groupId) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsGroupService.detail(groupId));
    }

    @Operation(summary = "强制解散群", description = "运营侧强制解散，用于处置违规群。")
    @PostMapping("/{groupId}/disband")
    public ApiResponse<Void> disband(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable String groupId, @RequestBody(required = false) OpsActionRequest request) {
        opsPermission.requireWrite(principal);
        opsGroupService.disband(principal.adminId(), groupId,
                request == null ? null : request.reason());
        return ApiResponse.ok();
    }
}
