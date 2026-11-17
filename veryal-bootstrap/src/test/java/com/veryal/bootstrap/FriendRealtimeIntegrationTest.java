package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.FriendRequestResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.access.websocket.WebSocketGatewayServer;
import com.veryal.bootstrap.support.WsTestClient;
import com.veryal.common.core.ApiResponse;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.EnvelopeCodec;
import com.veryal.protocol.protobuf.FriendNotify;
import com.veryal.protocol.protobuf.LoginRequest;
import com.veryal.protocol.protobuf.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 好友申请/接受实时通知：MQ 关闭时本机 Notifier 直推 FRIEND_NOTIFY。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FriendRealtimeIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private WebSocketGatewayServer webSocketGatewayServer;

    @Test
    void requestAndAcceptPushFriendNotify() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("frn_alice_" + suffix);
        UserResponse bob = register("frn_bob_" + suffix);
        String aliceDevice = "frn-a-" + suffix;
        String bobDevice = "frn-b-" + suffix;
        AuthResponse aliceLogin = login("frn_alice_" + suffix, aliceDevice);
        AuthResponse bobLogin = login("frn_bob_" + suffix, bobDevice);

        try (WsTestClient aliceWs = WsTestClient.connect(webSocketGatewayServer.boundPort());
             WsTestClient bobWs = WsTestClient.connect(webSocketGatewayServer.boundPort())) {
            loginBinary(aliceWs, aliceLogin.token(), aliceDevice);
            loginBinary(bobWs, bobLogin.token(), bobDevice);

            FriendRequestResponse request = post("/api/v1/friends/requests",
                    Map.of("toUserId", bob.userId(), "message", "hello"), aliceLogin.token(),
                    new ParameterizedTypeReference<ApiResponse<FriendRequestResponse>>() {
                    }).data();

            FriendNotify bobNotify = bobWs.awaitPayload(Command.FRIEND_NOTIFY, FriendNotify.parser());
            assertThat(bobNotify.getEventType()).isEqualTo(1);
            assertThat(bobNotify.getFromUserId()).isEqualTo(alice.userId());
            assertThat(bobNotify.getRequestId()).isEqualTo(request.requestId());

            post("/api/v1/friends/requests/" + request.requestId() + "/accept", Map.of(),
                    bobLogin.token(), new ParameterizedTypeReference<ApiResponse<FriendRequestResponse>>() {
                    });

            FriendNotify aliceNotify = awaitFriendType(aliceWs, 2);
            assertThat(aliceNotify.getEventType()).isEqualTo(2);
            assertThat(aliceNotify.getToUserId()).isEqualTo(bob.userId());

            post("/api/v1/blocks/" + alice.userId(), Map.of(), bobLogin.token(),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    });
            FriendNotify blockedNotify = awaitFriendType(bobWs, 5);
            assertThat(blockedNotify.getToUserId()).isEqualTo(alice.userId());

            rest.exchange("/api/v1/blocks/" + alice.userId(), HttpMethod.DELETE,
                    new HttpEntity<>(headers(bobLogin.token())),
                    new ParameterizedTypeReference<>() {
                    });
            assertThat(awaitFriendType(bobWs, 6).getEventType()).isEqualTo(6);
        }
    }

    private FriendNotify awaitFriendType(WsTestClient client, int expectedType) throws Exception {
        for (int i = 0; i < 5; i++) {
            ProtocolMessage message = client.read();
            if (message.command() != Command.FRIEND_NOTIFY) {
                continue;
            }
            FriendNotify notify = EnvelopeCodec.parsePayload(message.payload(),
                    FriendNotify.parser());
            if (notify.getEventType() == expectedType) {
                return notify;
            }
        }
        throw new AssertionError("friend notify type not received: " + expectedType);
    }

    private void loginBinary(WsTestClient client, String token, String deviceId) throws Exception {
        client.send(Command.LOGIN, LoginRequest.newBuilder()
                .setToken(token).setDeviceId(deviceId).setPlatform("LINUX").build());
        LoginResponse response = client.awaitPayload(Command.LOGIN_RESPONSE, LoginResponse.parser());
        assertThat(response.getCode()).isZero();
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username, String deviceId) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", deviceId, "platform", "LINUX", "deviceName", "test"), null,
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                }).data();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
