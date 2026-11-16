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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 平台操作审计查询，仅 PLATFORM_ADMIN 可用。
 */
@Tag(name = "运营-审计")
@RestController
@RequestMapping("/api/v1/platform/audit-logs")
@RequiredArgsConstructor
public class PlatformAuditController {

    private final PlatformAdminService platformAdminService;

    @Operation(summary = "审计日志", description = "记录运营侧的全部写操作，含操作人、动作、目标与时间。")
    @GetMapping
    public ApiResponse<List<PlatformAuditResponse>> list(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(platformAdminService.auditLogs(principal, limit).stream()
                .map(PlatformAuditResponse::from)
                .toList());
    }
}
