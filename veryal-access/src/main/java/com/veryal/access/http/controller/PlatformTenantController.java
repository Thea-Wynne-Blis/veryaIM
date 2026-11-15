package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateTenantRequest;
import com.veryal.access.http.dto.TenantResponse;
import com.veryal.application.tenant.TenantApplicationService;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.tenant.TenantStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台侧租户管理，使用独立 Admin Token，不走用户 JWT。
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final TenantApplicationService tenantApplicationService;

    @Value("${veryal.tenant.admin-token:}")
    private String adminToken;

    @PostMapping
    public ApiResponse<TenantResponse> create(@RequestHeader("X-Admin-Token") String token,
                                              @Valid @RequestBody CreateTenantRequest request) {
        requireAdmin(token);
        return ApiResponse.ok(TenantResponse.from(tenantApplicationService.create(request.code(),
                request.name(), request.maxUsers(), request.maxGroups(), request.maxStorageBytes())));
    }

    @GetMapping
    public ApiResponse<List<TenantResponse>> list(@RequestHeader("X-Admin-Token") String token,
                                                  @RequestParam(defaultValue = "50") int limit) {
        requireAdmin(token);
        return ApiResponse.ok(tenantApplicationService.list(limit).stream()
                .map(TenantResponse::from)
                .toList());
    }

    @PostMapping("/{tenantId}/disable")
    public ApiResponse<TenantResponse> disable(@RequestHeader("X-Admin-Token") String token,
                                               @PathVariable long tenantId) {
        requireAdmin(token);
        return ApiResponse.ok(TenantResponse.from(
                tenantApplicationService.updateStatus(tenantId, TenantStatus.DISABLED)));
    }

    @PostMapping("/{tenantId}/enable")
    public ApiResponse<TenantResponse> enable(@RequestHeader("X-Admin-Token") String token,
                                              @PathVariable long tenantId) {
        requireAdmin(token);
        return ApiResponse.ok(TenantResponse.from(
                tenantApplicationService.updateStatus(tenantId, TenantStatus.ACTIVE)));
    }

    private void requireAdmin(String token) {
        if (adminToken == null || adminToken.isBlank() || !adminToken.equals(token)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "invalid platform admin token");
        }
    }
}
