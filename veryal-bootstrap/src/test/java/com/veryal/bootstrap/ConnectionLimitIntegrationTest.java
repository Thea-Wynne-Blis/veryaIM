package com.veryal.bootstrap;

import com.veryal.access.websocket.WebSocketGatewayServer;
import com.veryal.bootstrap.support.WsTestClient;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.protobuf.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 单 IP 连接上限集成测试：第二个连接应被立即关闭，释放后可重新接入。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "veryal.gateway.max-connections-per-ip=1")
@ActiveProfiles("test")
class ConnectionLimitIntegrationTest {

    @Autowired
    private WebSocketGatewayServer webSocketGatewayServer;

    @Test
    void secondConnectionFromSameIpIsRejectedAndReleased() throws Exception {
        int port = webSocketGatewayServer.boundPort();
        WsTestClient first = WsTestClient.connect(port);
        try {
            Throwable rejected = catchThrowable(() -> WsTestClient.connect(port));
            assertThat(rejected).as("second connection should be rejected").isNotNull();
        } finally {
            first.close();
        }

        Thread.sleep(300);
        try (WsTestClient accepted = WsTestClient.connect(port)) {
            accepted.send(Command.LOGIN, LoginRequest.newBuilder()
                    .setToken("invalid-token").setDeviceId("limit-device").build());
            assertThat(accepted.await(Command.ERROR)).isNotNull();
        }
    }
}
