package com.veryal.access.http.dto;

import com.veryal.application.push.WebPushService;

/**
 * Web Push 前端配置。
 *
 * <p>{@code enabled=false} 时前端不应请求通知权限。</p>
 */
public record WebPushConfigResponse(boolean enabled, String publicKey) {

    public static WebPushConfigResponse from(WebPushService.WebPushConfigView view) {
        return new WebPushConfigResponse(view.enabled(), view.publicKey());
    }
}
