package com.veryal.access.http.controller;

import com.veryal.access.http.dto.WebPushConfigResponse;
import com.veryal.access.http.dto.WebPushSubscribeRequest;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.push.WebPushService;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 浏览器 Web Push：配置查询、订阅、退订。
 */
@Tag(name = "推送")
@RestController
@RequestMapping("/api/v1/push/web")
@RequiredArgsConstructor
public class WebPushController {

    private final WebPushService webPushService;

    @Operation(summary = "获取 Web Push 配置", description = "返回开关与 VAPID 公钥。`enabled=false` 时前端应直接跳过权限申请。")
    @GetMapping("/config")
    public ApiResponse<WebPushConfigResponse> config() {
        return ApiResponse.ok(WebPushConfigResponse.from(webPushService.config()));
    }

    @Operation(summary = "注册浏览器推送订阅", description = "上报 `endpoint`、`p256dh`、`auth`。服务端据此按 RFC 8291 加密并投递。")
    @PostMapping("/subscriptions")
    public ApiResponse<Void> subscribe(@CurrentUser AuthenticatedUser user,
                                       @Valid @RequestBody WebPushSubscribeRequest request) {
        webPushService.subscribe(user.userId(), request.endpoint(), request.p256dh(),
                request.auth(), request.userAgent());
        return ApiResponse.ok();
    }

    @Operation(summary = "取消浏览器推送订阅", description = "按 endpoint 退订。")
    @DeleteMapping("/subscriptions")
    public ApiResponse<Void> unsubscribe(@CurrentUser AuthenticatedUser user,
                                         @RequestParam String endpoint) {
        webPushService.unsubscribe(user.userId(), endpoint);
        return ApiResponse.ok();
    }
}
