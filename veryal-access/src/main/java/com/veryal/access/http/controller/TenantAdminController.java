package com.veryal.access.http.controller;

import com.veryal.access.http.dto.AddTenantAdminRequest;
import com.veryal.access.http.dto.TenantAdminResponse;
import com.veryal.access.http.dto.TenantUsageResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.tenant.TenantAdminService;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户内部管理：管理员与用量。
 */
@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;

    @GetMapping("/admins")
    public ApiResponse<List<TenantAdminResponse>> admins(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(tenantAdminService.list(user.tenantId(), user.userId()).stream()
                .map(TenantAdminResponse::from)
                .toList());
    }

    @PostMapping("/admins")
    public ApiResponse<TenantAdminResponse> add(@CurrentUser AuthenticatedUser user,
                                                @Valid @RequestBody AddTenantAdminRequest request) {
        return ApiResponse.ok(TenantAdminResponse.from(tenantAdminService.add(user.tenantId(),
                user.userId(), request.userId())));
    }

    @DeleteMapping("/admins/{userId}")
    public ApiResponse<Void> remove(@CurrentUser AuthenticatedUser user,
                                    @PathVariable long userId) {
        tenantAdminService.remove(user.tenantId(), user.userId(), userId);
        return ApiResponse.ok();
    }

    @GetMapping("/usage")
    public ApiResponse<TenantUsageResponse> usage(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(TenantUsageResponse.from(
                tenantAdminService.usage(user.tenantId(), user.userId())));
    }
}
