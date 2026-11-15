package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateSensitiveWordRequest;
import com.veryal.access.http.dto.SensitiveWordResponse;
import com.veryal.access.http.security.PlatformAdminFilter;
import com.veryal.application.moderation.SensitiveWordService;
import com.veryal.application.ops.OpsPermission;
import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 运营后台敏感词管理。
 */
@RestController
@RequestMapping("/api/v1/platform/ops/sensitive-words")
@RequiredArgsConstructor
public class OpsSensitiveWordController {

    private final SensitiveWordService sensitiveWordService;
    private final OpsPermission opsPermission;

    @GetMapping
    public ApiResponse<List<SensitiveWordResponse>> list(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @RequestParam(defaultValue = "200") int limit) {
        opsPermission.requireRead(principal);
        return ApiResponse.ok(sensitiveWordService.list(limit).stream()
                .map(SensitiveWordResponse::from)
                .toList());
    }

    @PostMapping
    public ApiResponse<SensitiveWordResponse> add(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @Valid @RequestBody CreateSensitiveWordRequest request) {
        opsPermission.requireWrite(principal);
        return ApiResponse.ok(SensitiveWordResponse.from(
                sensitiveWordService.add(principal.adminId(), request.word())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestAttribute(PlatformAdminFilter.ATTRIBUTE) PlatformPrincipal principal,
            @PathVariable long id) {
        opsPermission.requireWrite(principal);
        sensitiveWordService.delete(principal.adminId(), id);
        return ApiResponse.ok();
    }
}
