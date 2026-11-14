package com.veryal.bootstrap;

import com.veryal.access.tcp.TcpGatewayServer;
import com.veryal.bootstrap.support.TcpTestClient;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.protobuf.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单 IP 连接上限集成测试：第二个连接应被立即关闭，释放后可重新接入。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "veryal.gateway.max-connections-per-ip=1")
@ActiveProfiles("test")
class ConnectionLimitIntegrationTest {

    @Autowired
    private TcpGatewayServer tcpGatewayServer;

    @Test
    void secondConnectionFromSameIpIsRejectedAndReleased() throws Exception {
        try (TcpTestClient first = TcpTestClient.connect(tcpGatewayServer.boundPort())) {
            try (TcpTestClient second = TcpTestClient.connect(tcpGatewayServer.boundPort())) {
                assertThatThrownBy(second::read).isInstanceOf(IOException.class);
            }
        }

        Thread.sleep(300);
        try (TcpTestClient accepted = TcpTestClient.connect(tcpGatewayServer.boundPort())) {
            accepted.send(Command.LOGIN, LoginRequest.newBuilder()
                    .setToken("invalid-token").setDeviceId("limit-device").build());
            assertThat(accepted.await(Command.ERROR)).isNotNull();
        }
    }
}
