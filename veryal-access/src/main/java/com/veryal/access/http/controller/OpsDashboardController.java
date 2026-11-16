package com.veryal.access.http.controller;

import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.ops.OpsDashboardService;
import com.veryal.application.ops.OpsPermission;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 运营概览。
 */
@Tag(name = "运营-概览")
@RestController
@RequestMapping("/api/v1/platform/ops/dashboard")
@RequiredArgsConstructor
public class OpsDashboardController {

    private final OpsDashboardService opsDashboardService;
    private final OpsPermission opsPermission;

    @Operation(summary = "平台概览看板", description = "返回用户数、会话数、消息量、待处理举报等关键指标。")
    @GetMapping
    public ApiResponse<OpsDashboardService.DashboardView> dashboard(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsDashboardService.dashboard());
    }
}
