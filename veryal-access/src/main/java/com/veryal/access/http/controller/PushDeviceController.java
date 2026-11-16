package com.veryal.access.http.controller;

import com.veryal.access.http.dto.PushDeviceResponse;
import com.veryal.access.http.dto.RegisterPushDeviceRequest;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.push.PushDeviceService;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 推送设备 token 注册。
 */
@Tag(name = "推送")
@RestController
@RequestMapping("/api/v1/push/devices")
@RequiredArgsConstructor
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    @Operation(summary = "注册设备推送 token", description = "上报 APNs / FCM 的设备令牌。同一 deviceId 重复上报会更新而不会重复堆积。")
    @PostMapping
    public ApiResponse<PushDeviceResponse> register(@CurrentUser AuthenticatedUser user,
                                                    @Valid @RequestBody RegisterPushDeviceRequest request) {
        return ApiResponse.ok(PushDeviceResponse.from(pushDeviceService.register(user.userId(),
                request.deviceId(), request.platform(), request.token())));
    }

    @Operation(summary = "我的推送设备", description = "列出已注册的设备与启用状态。")
    @GetMapping
    public ApiResponse<List<PushDeviceResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(pushDeviceService.list(user.userId()).stream()
                .map(PushDeviceResponse::from)
                .toList());
    }

    @Operation(summary = "注销推送设备", description = "用户关闭通知或退出登录时调用。")
    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> unregister(@CurrentUser AuthenticatedUser user,
                                        @PathVariable String deviceId) {
        pushDeviceService.unregister(user.userId(), deviceId);
        return ApiResponse.ok();
    }
}
