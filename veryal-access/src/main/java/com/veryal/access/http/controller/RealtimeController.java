package com.veryal.access.http.controller;

import com.veryal.access.http.realtime.RealtimeEventHub;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 网页端实时事件流。
 */
@Tag(name = "实时事件")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeEventHub realtimeEventHub;

    @Operation(summary = "实时事件流", description = "SSE 长连接：服务端推送 `message` 事件（新消息），"
            + "每 15 秒发送心跳注释。客户端断线后自行重连，轮询接口仍可作为兜底。")
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@CurrentUser AuthenticatedUser user) {
        return realtimeEventHub.subscribe(user.userId());
    }
}
