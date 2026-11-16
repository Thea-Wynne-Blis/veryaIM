package com.veryal.access.http.controller;

import com.veryal.access.http.dto.OpsHandleReportRequest;
import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.ops.OpsPermission;
import com.veryal.application.ops.OpsReportService;
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
 * 运营后台举报处理。
 */
@Tag(name = "运营-举报")
@RestController
@RequestMapping("/api/v1/platform/ops/reports")
@RequiredArgsConstructor
public class OpsReportController {

    private final OpsReportService opsReportService;
    private final OpsPermission opsPermission;

    @Operation(summary = "举报工单列表", description = "按状态过滤，支持分页。")
    @GetMapping
    public ApiResponse<List<OpsReportService.OpsReportView>> list(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsReportService.list(status, limit));
    }

    @Operation(summary = "举报工单详情", description = "返回举报内容、被举报对象与历史处理记录。")
    @GetMapping("/{reportId}")
    public ApiResponse<OpsReportService.OpsReportView> get(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long reportId) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsReportService.get(reportId));
    }

    @Operation(summary = "处理举报工单", description = "填写处理结论与动作，工单状态随之流转。")
    @PostMapping("/{reportId}/handle")
    public ApiResponse<OpsReportService.OpsReportView> handle(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long reportId, @RequestBody OpsHandleReportRequest request) {
        opsPermission.requireWrite(principal);
        return ApiResponse.ok(opsReportService.handle(principal.adminId(), reportId,
                request.status(), request.note()));
    }
}
