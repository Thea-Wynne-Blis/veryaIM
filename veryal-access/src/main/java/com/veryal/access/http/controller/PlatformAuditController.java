package com.veryal.access.http.controller;

import com.veryal.access.http.dto.PlatformAuditResponse;
import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.platform.PlatformAdminService;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台操作审计查询，仅 PLATFORM_ADMIN 可用。
 */
@RestController
@RequestMapping("/api/v1/platform/audit-logs")
@RequiredArgsConstructor
public class PlatformAuditController {

    private final PlatformAdminService platformAdminService;

    @GetMapping
    public ApiResponse<List<PlatformAuditResponse>> list(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(platformAdminService.auditLogs(principal, limit).stream()
                .map(PlatformAuditResponse::from)
                .toList());
    }
}
