package com.veryal.bootstrap;

import com.veryal.access.websocket.WebSocketGatewayServer;
import com.veryal.application.identity.AuthApplicationService;
import com.veryal.application.identity.AuthResult;
import com.veryal.application.identity.LoginCommand;
import com.veryal.application.identity.RegisterCommand;
import com.veryal.application.identity.UserProfile;
import com.veryal.bootstrap.support.TestFrames;
import com.veryal.common.core.TenantIds;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.EnvelopeCodec;
import com.veryal.protocol.protobuf.HeartbeatRequest;
import com.veryal.protocol.protobuf.LoginRequest;
import com.veryal.protocol.protobuf.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * WebSocket Gateway 端到端测试：真实握手 + 二进制协议帧 + 认证 + 心跳。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketGatewayIntegrationTest {

    @Autowired
    private WebSocketGatewayServer webSocketGatewayServer;

    @Autowired
    private AuthApplicationService authApplicationService;

    @Test
    void clientCanLoginAndHeartbeatOverWebSocket() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String username = "ws_user_" + suffix;
        authApplicationService.register(TenantIds.DEFAULT_TENANT_ID,
                new RegisterCommand(username, "password123", "WebSocket User"));
        AuthResult login = authApplicationService.login(TenantIds.DEFAULT_TENANT_ID,
                new LoginCommand(username, "password123", "ws-device-" + suffix, "WEB", "browser"));

        int port = webSocketGatewayServer.boundPort();
        assertThat(port).isPositive();

        BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);
                buffer.writeBytes(chunk);
                if (last) {
                    frames.add(buffer.toByteArray());
                    buffer.reset();
                }
                webSocket.request(1);
                return null;
            }
        };

        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), listener)
                .get(10, TimeUnit.SECONDS);
        try {
            socket.sendBinary(ByteBuffer.wrap(TestFrames.encode(Command.LOGIN, 1L, "ws-login",
                    LoginRequest.newBuilder()
                            .setToken(login.token())
                            .setDeviceId("ws-device-" + suffix)
                            .setPlatform("WEB")
                            .build())), true).get(5, TimeUnit.SECONDS);

            ProtocolMessage loginResponse = await(frames, Command.LOGIN_RESPONSE);
            LoginResponse parsed = EnvelopeCodec.parsePayload(loginResponse.payload(), LoginResponse.parser());
            assertThat(parsed.getCode()).isZero();
            assertThat(parsed.getUserId()).isEqualTo(login.user().userId());

            socket.sendBinary(ByteBuffer.wrap(TestFrames.encode(Command.HEARTBEAT, 2L, "ws-ping",
                    HeartbeatRequest.newBuilder().setClientTime(System.currentTimeMillis()).build())),
                    true).get(5, TimeUnit.SECONDS);
            assertThat(await(frames, Command.HEARTBEAT_ACK)).isNotNull();
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }
    }

    private ProtocolMessage await(BlockingQueue<byte[]> frames, Command expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            byte[] frame = frames.poll(100, TimeUnit.MILLISECONDS);
            if (frame == null) {
                continue;
            }
            ProtocolMessage message = TestFrames.decode(frame).message();
            if (message.command() == expected) {
                return message;
            }
        }
        fail("timed out waiting for " + expected);
        return null;
    }
}
