package com.veryal.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.access.websocket.WebSocketGatewayServer;
import com.veryal.application.identity.AuthApplicationService;
import com.veryal.application.identity.AuthResult;
import com.veryal.application.identity.LoginCommand;
import com.veryal.application.identity.RegisterCommand;
import com.veryal.common.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 票据建连端到端测试：HTTP 换票 → WebSocket 握手即登录 → JSON 心跳。
 *
 * <p>浏览器不能给 WebSocket 握手设置 Authorization 头，这条链路是 Web 端实时通道的入口。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WsTicketIntegrationTest {

    @Autowired
    private WebSocketGatewayServer webSocketGatewayServer;

    @Autowired
    private AuthApplicationService authApplicationService;

    @Autowired
    private com.veryal.access.core.ConnectionRegistry connectionRegistry;

    @LocalServerPort
    private int httpPort;

    @Test
    void ticketAuthenticatesHandshakeAndHandlesJsonHeartbeat() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String username = "ws_ticket_" + suffix;
        authApplicationService.register(new RegisterCommand(username, "password123", "Ticket User"));
        AuthResult login = authApplicationService.login(new LoginCommand(username, "password123",
                "web-" + suffix, "WEB", "browser"));

        String ticket = issueTicket(login.token());
        int wsPort = webSocketGatewayServer.boundPort();
        assertThat(wsPort).isPositive();

        BlockingQueue<String> textFrames = new LinkedBlockingQueue<>();
        WebSocket socket = connect(wsPort, ticket, textFrames);
        try {
            JsonNode ready = awaitCmd(textFrames, "ready");
            assertThat(ready.path("data").path("userId").asText())
                    .isEqualTo(Long.toString(login.user().userId()));
            // 票据认证的连接必须进按用户索引，否则实时推送找不到它。
            assertThat(connectionRegistry.findByUserId(login.user().userId())).isNotEmpty();

            socket.sendText("{\"cmd\":\"ping\",\"seq\":1}", true).get(5, TimeUnit.SECONDS);
            JsonNode pong = awaitCmd(textFrames, "pong");
            assertThat(pong.path("data").path("ts").asLong()).isPositive();
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }

        // 一次性票据：重复使用必须被服务端关闭（1008 policy violation）。
        BlockingQueue<Integer> closeCodes = new LinkedBlockingQueue<>();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                closeCodes.add(statusCode);
                return null;
            }
        };
        WebSocket reused = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:" + wsPort + "/ws?ticket=" + ticket
                        + "&format=json"), listener)
                .get(10, TimeUnit.SECONDS);
        Integer closeCode = closeCodes.poll(5, TimeUnit.SECONDS);
        assertThat(closeCode).isEqualTo(1008);
        reused.abort();
    }

    private String issueTicket(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + httpPort + "/api/v1/auth/ws-ticket"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JsonUtils.mapper().readTree(response.body());
        String ticket = body.path("data").path("ticket").asText();
        assertThat(ticket).isNotBlank();
        return ticket;
    }

    private WebSocket connect(int port, String ticket, BlockingQueue<String> frames) throws Exception {
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                frames.add(data.toString());
                webSocket.request(1);
                return null;
            }
        };
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws?ticket=" + ticket + "&format=json"),
                        listener)
                .get(10, TimeUnit.SECONDS);
    }

    private JsonNode awaitCmd(BlockingQueue<String> frames, String cmd) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String text = frames.poll(100, TimeUnit.MILLISECONDS);
            if (text == null) {
                continue;
            }
            JsonNode frame = JsonUtils.mapper().readTree(text);
            if (cmd.equals(frame.path("cmd").asText())) {
                return frame;
            }
        }
        fail("timed out waiting for cmd " + cmd);
        return null;
    }
}
