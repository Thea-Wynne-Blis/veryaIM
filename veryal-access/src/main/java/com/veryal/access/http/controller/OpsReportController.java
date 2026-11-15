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

/**
 * 运营后台举报处理。
 */
@RestController
@RequestMapping("/api/v1/platform/ops/reports")
@RequiredArgsConstructor
public class OpsReportController {

    private final OpsReportService opsReportService;
    private final OpsPermission opsPermission;

    @GetMapping
    public ApiResponse<List<OpsReportService.OpsReportView>> list(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsReportService.list(status, limit));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<OpsReportService.OpsReportView> get(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long reportId) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(opsReportService.get(reportId));
    }

    @PostMapping("/{reportId}/handle")
    public ApiResponse<OpsReportService.OpsReportView> handle(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long reportId, @RequestBody OpsHandleReportRequest request) {
        opsPermission.requireWrite(principal);
        return ApiResponse.ok(opsReportService.handle(principal.adminId(), reportId,
                request.status(), request.note()));
    }
}
