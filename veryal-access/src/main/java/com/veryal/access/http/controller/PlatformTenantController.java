package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateTenantRequest;
import com.veryal.access.http.dto.TenantResponse;
import com.veryal.access.http.dto.DeleteTenantRequest;
import com.veryal.access.http.dto.TenantAdminResponse;
import com.veryal.access.http.dto.TenantExportResponse;
import com.veryal.access.http.dto.TenantUsageResponse;
import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.platform.PlatformAuditRecorder;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.application.tenant.TenantAdminService;
import com.veryal.application.tenant.TenantApplicationService;
import com.veryal.application.tenant.TenantDataService;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.platform.PlatformAdminRole;
import com.veryal.domain.tenant.Tenant;
import com.veryal.domain.tenant.TenantStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

/**
 * 平台侧租户管理；由 PlatformAdminFilter 校验平台 Token。
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final TenantApplicationService tenantApplicationService;
    private final TenantAdminService tenantAdminService;
    private final TenantDataService tenantDataService;
    private final PlatformAuditRecorder platformAuditRecorder;

    @PostMapping
    public ApiResponse<TenantResponse> create(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @Valid @RequestBody CreateTenantRequest request) {
        requireAdmin(principal);
        Tenant tenant = tenantApplicationService.create(request.code(), request.name(),
                request.maxUsers(), request.maxGroups(), request.maxStorageBytes());
        platformAuditRecorder.record(principal.adminId(), "TENANT_CREATE", "tenant",
                Long.toString(tenant.id()), "code=" + tenant.code());
        return ApiResponse.ok(TenantResponse.from(tenant));
    }

    @GetMapping
    public ApiResponse<List<TenantResponse>> list(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(tenantApplicationService.list(limit).stream()
                .map(TenantResponse::from)
                .toList());
    }

    @PostMapping("/{tenantId}/disable")
    public ApiResponse<TenantResponse> disable(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long tenantId) {
        requireAdmin(principal);
        Tenant tenant = tenantApplicationService.updateStatus(tenantId, TenantStatus.DISABLED);
        platformAuditRecorder.record(principal.adminId(), "TENANT_DISABLE", "tenant",
                Long.toString(tenantId), null);
        return ApiResponse.ok(TenantResponse.from(tenant));
    }

    @PostMapping("/{tenantId}/enable")
    public ApiResponse<TenantResponse> enable(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long tenantId) {
        requireAdmin(principal);
        Tenant tenant = tenantApplicationService.updateStatus(tenantId, TenantStatus.ACTIVE);
        platformAuditRecorder.record(principal.adminId(), "TENANT_ENABLE", "tenant",
                Long.toString(tenantId), null);
        return ApiResponse.ok(TenantResponse.from(tenant));
    }

    @PostMapping("/{tenantId}/admins/{userId}")
    public ApiResponse<TenantAdminResponse> appointAdmin(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long tenantId, @PathVariable long userId) {
        requireAdmin(principal);
        TenantAdminResponse response = TenantAdminResponse.from(
                tenantAdminService.appoint(tenantId, userId));
        platformAuditRecorder.record(principal.adminId(), "TENANT_ADMIN_APPOINT", "tenant_admin",
                tenantId + ":" + userId, null);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{tenantId}/usage")
    public ApiResponse<TenantUsageResponse> usage(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long tenantId) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(TenantUsageResponse.from(tenantAdminService.usageOf(tenantId)));
    }

    @GetMapping("/{tenantId}/export")
    public ApiResponse<TenantExportResponse> export(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long tenantId) {
        requireAdmin(principal);
        return ApiResponse.ok(TenantExportResponse.from(tenantDataService.export(tenantId)));
    }

    @DeleteMapping("/{tenantId}")
    public ApiResponse<Void> delete(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long tenantId, @Valid @RequestBody DeleteTenantRequest request) {
        requireAdmin(principal);
        tenantDataService.delete(tenantId, request.confirmCode());
        platformAuditRecorder.record(principal.adminId(), "TENANT_DELETE", "tenant",
                Long.toString(tenantId), "confirmCode matched");
        return ApiResponse.ok();
    }

    private void requireAdmin(PlatformPrincipal principal) {
        if (principal == null || principal.role() != PlatformAdminRole.PLATFORM_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "platform admin permission required");
        }
    }
}
