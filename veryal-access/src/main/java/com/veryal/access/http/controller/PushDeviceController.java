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

/**
 * 推送设备 token 注册。
 */
@RestController
@RequestMapping("/api/v1/push/devices")
@RequiredArgsConstructor
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    @PostMapping
    public ApiResponse<PushDeviceResponse> register(@CurrentUser AuthenticatedUser user,
                                                    @Valid @RequestBody RegisterPushDeviceRequest request) {
        return ApiResponse.ok(PushDeviceResponse.from(pushDeviceService.register(user.userId(),
                request.deviceId(), request.platform(), request.token())));
    }

    @GetMapping
    public ApiResponse<List<PushDeviceResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(pushDeviceService.list(user.userId()).stream()
                .map(PushDeviceResponse::from)
                .toList());
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> unregister(@CurrentUser AuthenticatedUser user,
                                        @PathVariable String deviceId) {
        pushDeviceService.unregister(user.userId(), deviceId);
        return ApiResponse.ok();
    }
}
