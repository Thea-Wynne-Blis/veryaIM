package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.MessagePageResponse;
import com.veryal.access.http.dto.MessageResponse;
import com.veryal.access.http.dto.SendMessageResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
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
 * 消息撤回：仅发送者可在窗口内撤回，历史消息不回传原文。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MessageRecallIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void onlySenderCanRecallAndHistoryHidesContent() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("rc_alice_" + suffix);
        UserResponse bob = register("rc_bob_" + suffix);
        AuthResponse aliceLogin = login("rc_alice_" + suffix, "rc-a-" + suffix);
        AuthResponse bobLogin = login("rc_bob_" + suffix, "rc-b-" + suffix);

        String conversationId = createConversation(bob, aliceLogin.token());
        SendMessageResponse sent = send(conversationId, "rc-msg-" + suffix, aliceLogin.token());
        long messageId = sent.message().messageId();

        ResponseEntity<ApiResponse<MessageResponse>> forbidden = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages/" + messageId + "/recall",
                HttpMethod.POST, new HttpEntity<>(headers(bobLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        ApiResponse<MessageResponse> recalled = recall(conversationId, messageId, aliceLogin.token());
        assertThat(recalled.data().recalled()).isTrue();
        assertThat(recalled.data().content()).isEmpty();
        assertThat(recalled.data().recalledAt()).isNotNull();

        // 重复撤回保持幂等
        ApiResponse<MessageResponse> again = recall(conversationId, messageId, aliceLogin.token());
        assertThat(again.data().recalled()).isTrue();

        ApiResponse<MessagePageResponse> history = get(
                "/api/v1/conversations/" + conversationId + "/messages", bobLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        MessageResponse inHistory = history.data().messages().get(0);
        assertThat(inHistory.messageId()).isEqualTo(messageId);
        assertThat(inHistory.recalled()).isTrue();
        assertThat(inHistory.content()).isEmpty();
    }

    private ApiResponse<MessageResponse> recall(String conversationId, long messageId, String token) {
        ResponseEntity<ApiResponse<MessageResponse>> response = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages/" + messageId + "/recall",
                HttpMethod.POST, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private SendMessageResponse send(String conversationId, String clientMessageId, String token) {
        ApiResponse<SendMessageResponse> response = post(
                "/api/v1/conversations/" + conversationId + "/messages",
                Map.of("conversationId", conversationId, "clientMessageId", clientMessageId,
                        "type", "TEXT", "content", "to be recalled"),
                token, new ParameterizedTypeReference<>() {
                });
        return response.data();
    }

    private String createConversation(UserResponse peer, String token) {
        ApiResponse<com.veryal.access.http.dto.ConversationResponse> response = post(
                "/api/v1/conversations", Map.of("peerUserId", peer.userId()), token,
                new ParameterizedTypeReference<>() {
                });
        return response.data().conversationId();
    }

    private UserResponse register(String username) {
        ApiResponse<UserResponse> response = post("/api/v1/auth/register",
                Map.of("username", username, "password", "password123", "nickname", username),
                null, new ParameterizedTypeReference<>() {
                });
        return response.data();
    }

    private AuthResponse login(String username, String deviceId) {
        ApiResponse<AuthResponse> response = post("/api/v1/auth/login",
                Map.of("username", username, "password", "password123", "deviceId", deviceId,
                        "platform", "LINUX", "deviceName", "test"),
                null, new ParameterizedTypeReference<>() {
                });
        return response.data();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private <T> ApiResponse<T> get(String path, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
