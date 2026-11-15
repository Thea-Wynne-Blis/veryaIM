package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateReportRequest;
import com.veryal.access.http.dto.ReportResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.report.ReportApplicationService;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.report.ReportTargetType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 用户举报。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportApplicationService reportApplicationService;

    @PostMapping
    public ApiResponse<ReportResponse> submit(@CurrentUser AuthenticatedUser user,
                                              @Valid @RequestBody CreateReportRequest request) {
        ReportTargetType type;
        try {
            type = ReportTargetType.valueOf(request.targetType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid targetType");
        }
        return ApiResponse.ok(ReportResponse.from(reportApplicationService.submit(user.userId(),
                type, request.targetId(), request.reason(), request.detail())));
    }

    @GetMapping("/me")
    public ApiResponse<List<ReportResponse>> mine(@CurrentUser AuthenticatedUser user,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(reportApplicationService.mine(user.userId(), limit).stream()
                .map(ReportResponse::from)
                .toList());
    }
}
