package com.veryal.access.http.dto;

import com.veryal.application.push.PushDeviceService;

import java.time.Instant;

/**
 * 推送设备响应。
 */
public record PushDeviceResponse(String deviceId, String platform, String token, boolean enabled,
                                 Instant updatedAt) {

    public static PushDeviceResponse from(PushDeviceService.PushDeviceView view) {
        return new PushDeviceResponse(view.deviceId(), view.platform(), view.token(),
                view.enabled(), view.updatedAt());
    }
}
