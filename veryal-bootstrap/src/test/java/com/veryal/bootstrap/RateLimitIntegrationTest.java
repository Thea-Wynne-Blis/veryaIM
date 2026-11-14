package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.ErrorCode;
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
 * 限流集成测试：test profile 将 message-send 配置为令牌桶容量 5、补充速率 0。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void sixthsMessageIsRateLimited() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse sender = register("rl_sender_" + suffix);
        UserResponse receiver = register("rl_receiver_" + suffix);
        AuthResponse senderLogin = login("rl_sender_" + suffix, "rl-a-" + suffix);
        login("rl_receiver_" + suffix, "rl-b-" + suffix);

        ApiResponse<com.veryal.access.http.dto.ConversationResponse> conversation = post(
                "/api/v1/conversations", Map.of("peerUserId", receiver.userId()), senderLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        String conversationId = conversation.data().conversationId();

        for (int i = 1; i <= 5; i++) {
            send(conversationId, "rl-" + suffix + "-" + i, "msg-" + i, senderLogin.token());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(senderLogin.token());
        ResponseEntity<ApiResponse<Void>> limited = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("conversationId", conversationId,
                        "clientMessageId", "rl-" + suffix + "-6", "type", "TEXT",
                        "content", "msg-6"), headers),
                new ParameterizedTypeReference<>() {
                });

        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getBody()).isNotNull();
        assertThat(limited.getBody().code()).isEqualTo(ErrorCode.RATE_LIMITED.code());
        assertThat(sender.userId()).isPositive();
    }

    private void send(String conversationId, String clientMessageId, String content, String token) {
        ApiResponse<Void> response = post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("conversationId", conversationId, "clientMessageId", clientMessageId,
                        "type", "TEXT", "content", content),
                token, new ParameterizedTypeReference<>() {
                });
        assertThat(response.successful()).isTrue();
    }

    private UserResponse register(String username) {
        ApiResponse<UserResponse> response = post("/api/v1/auth/register",
                Map.of("username", username, "password", "password123", "nickname", username),
                null, new ParameterizedTypeReference<>() {
                });
        assertThat(response.successful()).isTrue();
        return response.data();
    }

    private AuthResponse login(String username, String deviceId) {
        ApiResponse<AuthResponse> response = post("/api/v1/auth/login",
                Map.of("username", username, "password", "password123", "deviceId", deviceId,
                        "platform", "LINUX", "deviceName", "test"),
                null, new ParameterizedTypeReference<>() {
                });
        assertThat(response.successful()).isTrue();
        return response.data();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
