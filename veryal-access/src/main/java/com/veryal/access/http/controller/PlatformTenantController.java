package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateTenantRequest;
import com.veryal.access.http.dto.TenantResponse;
import com.veryal.application.tenant.TenantApplicationService;
import com.veryal.common.core.ApiResponse;
import com.veryal.domain.tenant.TenantStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台侧租户管理；由 PlatformAdminFilter 校验平台 Token。
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final TenantApplicationService tenantApplicationService;

    @PostMapping
    public ApiResponse<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(TenantResponse.from(tenantApplicationService.create(request.code(),
                request.name(), request.maxUsers(), request.maxGroups(), request.maxStorageBytes())));
    }

    @GetMapping
    public ApiResponse<List<TenantResponse>> list(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(tenantApplicationService.list(limit).stream()
                .map(TenantResponse::from)
                .toList());
    }

    @PostMapping("/{tenantId}/disable")
    public ApiResponse<TenantResponse> disable(@PathVariable long tenantId) {
        return ApiResponse.ok(TenantResponse.from(
                tenantApplicationService.updateStatus(tenantId, TenantStatus.DISABLED)));
    }

    @PostMapping("/{tenantId}/enable")
    public ApiResponse<TenantResponse> enable(@PathVariable long tenantId) {
        return ApiResponse.ok(TenantResponse.from(
                tenantApplicationService.updateStatus(tenantId, TenantStatus.ACTIVE)));
    }
}
