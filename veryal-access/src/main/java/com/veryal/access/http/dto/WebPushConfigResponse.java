package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.push.WebPushService;

/**
 * Web Push 前端配置。
 *
 * <p>{@code enabled=false} 时前端不应请求通知权限。</p>
 */
public record WebPushConfigResponse(
                                 @Schema(description = "是否启用")
                                 boolean enabled,
                                 @Schema(description = "VAPID 公钥，作为 applicationServerKey 传给浏览器")
                                 String publicKey
) {

    public static WebPushConfigResponse from(WebPushService.WebPushConfigView view) {
        return new WebPushConfigResponse(view.enabled(), view.publicKey());
    }
}
