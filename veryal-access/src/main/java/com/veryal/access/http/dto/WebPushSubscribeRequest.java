package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册浏览器 Web Push 订阅。
 */
public record WebPushSubscribeRequest(
        @NotBlank @Size(max = 2048) String endpoint,
        @NotBlank @Size(max = 128) String p256dh,
        @NotBlank @Size(max = 64) String auth,
        @Size(max = 255) String userAgent) {
}
