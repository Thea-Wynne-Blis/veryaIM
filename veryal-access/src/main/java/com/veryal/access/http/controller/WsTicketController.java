package com.veryal.access.http.controller;

import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import com.veryal.domain.identity.WsTicketPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket 建连票据。
 *
 * <p>浏览器不能给 WebSocket 握手设置请求头，所以先用 Bearer Token 换一张
 * 60 秒有效、只能使用一次的 ticket，再以 {@code /ws?ticket=...&format=json} 建连。</p>
 */
@Tag(name = "实时事件")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WsTicketController {

    private final WsTicketPort wsTicketPort;

    @Operation(summary = "签发 WebSocket 建连票据",
            description = "返回 60 秒有效、只能使用一次的 ticket；随后用 `/ws?ticket=<ticket>&format=json` 建立长连接。")
    @PostMapping("/auth/ws-ticket")
    public ApiResponse<WsTicketResponse> issue(@CurrentUser AuthenticatedUser user) {
        String ticket = wsTicketPort.issue(new WsTicketPort.TicketPayload(
                user.userId(), user.deviceId(), user.tokenId()));
        return ApiResponse.ok(new WsTicketResponse(ticket, wsTicketPort.ttlSeconds(),
                user.deviceId(), "/ws?format=json"));
    }

    public record WsTicketResponse(String ticket, long expiresInSeconds, String deviceId, String path) {
    }
}
