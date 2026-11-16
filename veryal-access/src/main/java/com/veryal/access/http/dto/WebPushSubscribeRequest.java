package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 注册浏览器 Web Push 订阅。
 */
public record WebPushSubscribeRequest(
                                 @Schema(description = "浏览器推送端点地址")
                                 @NotBlank @Size(max = 2048) String endpoint,
                                 @Schema(description = "订阅端公钥（base64url）")
                                 @NotBlank @Size(max = 128) String p256dh,
                                 @Schema(description = "订阅端认证密钥（base64url）")
                                 @NotBlank @Size(max = 64) String auth,
                                 @Schema(description = "客户端 UA，便于排查")
                                 @Size(max = 255) String userAgent
) {
}
