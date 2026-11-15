package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册推送设备。
 */
public record RegisterPushDeviceRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @NotBlank @Size(max = 16) String platform,
        @NotBlank @Size(max = 512) String token) {
}
