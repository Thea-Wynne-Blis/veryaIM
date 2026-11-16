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

/**
 * 浏览器 Web Push：配置查询、订阅、退订。
 */
@RestController
@RequestMapping("/api/v1/push/web")
@RequiredArgsConstructor
public class WebPushController {

    private final WebPushService webPushService;

    @GetMapping("/config")
    public ApiResponse<WebPushConfigResponse> config() {
        return ApiResponse.ok(WebPushConfigResponse.from(webPushService.config()));
    }

    @PostMapping("/subscriptions")
    public ApiResponse<Void> subscribe(@CurrentUser AuthenticatedUser user,
                                       @Valid @RequestBody WebPushSubscribeRequest request) {
        webPushService.subscribe(user.userId(), request.endpoint(), request.p256dh(),
                request.auth(), request.userAgent());
        return ApiResponse.ok();
    }

    @DeleteMapping("/subscriptions")
    public ApiResponse<Void> unsubscribe(@CurrentUser AuthenticatedUser user,
                                         @RequestParam String endpoint) {
        webPushService.unsubscribe(user.userId(), endpoint);
        return ApiResponse.ok();
    }
}
