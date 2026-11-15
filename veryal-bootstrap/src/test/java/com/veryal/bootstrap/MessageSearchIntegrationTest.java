package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.MessagePageResponse;
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
 * 会话内消息搜索：文本匹配、通配符转义、非成员 403、撤回消息排除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MessageSearchIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void searchesTextAndExcludesRecalledMessages() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("se_alice_" + suffix);
        UserResponse bob = register("se_bob_" + suffix);
        UserResponse outsider = register("se_out_" + suffix);
        AuthResponse aliceLogin = login("se_alice_" + suffix, "se-a-" + suffix);
        AuthResponse bobLogin = login("se_bob_" + suffix, "se-b-" + suffix);
        AuthResponse outsiderLogin = login("se_out_" + suffix, "se-c-" + suffix);

        String conversationId = createConversation(bob, aliceLogin.token());
        SendMessageResponse first = send(conversationId, "se-1-" + suffix, "hello world",
                aliceLogin.token());
        send(conversationId, "se-2-" + suffix, "other message", aliceLogin.token());
        SendMessageResponse secret = send(conversationId, "se-3-" + suffix, "secret hello",
                aliceLogin.token());

        ApiResponse<MessagePageResponse> hello = search(conversationId, "hello", bobLogin.token(), 200);
        assertThat(hello.data().messages()).hasSize(2);

        recall(conversationId, secret.message().messageId(), aliceLogin.token());
        ApiResponse<MessagePageResponse> afterRecall = search(conversationId, "hello", bobLogin.token(), 200);
        assertThat(afterRecall.data().messages()).hasSize(1);
        assertThat(afterRecall.data().messages().get(0).messageId()).isEqualTo(first.message().messageId());

        ApiResponse<MessagePageResponse> wildcard = search(conversationId, "%", bobLogin.token(), 200);
        assertThat(wildcard.data().messages()).isEmpty();

        ResponseEntity<ApiResponse<MessagePageResponse>> forbidden = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages/search?keyword=hello",
                HttpMethod.GET, new HttpEntity<>(headers(outsiderLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(outsider.userId()).isPositive();
    }

    private ApiResponse<MessagePageResponse> search(String conversationId, String keyword,
                                                    String token, int expectedStatus) {
        ResponseEntity<ApiResponse<MessagePageResponse>> response = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages/search?keyword="
                        + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8),
                HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private void recall(String conversationId, long messageId, String token) {
        ResponseEntity<ApiResponse<com.veryal.access.http.dto.MessageResponse>> response =
                rest.exchange("/api/v1/conversations/" + conversationId + "/messages/" + messageId
                                + "/recall", HttpMethod.POST, new HttpEntity<>(headers(token)),
                        new ParameterizedTypeReference<>() {
                        });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private SendMessageResponse send(String conversationId, String clientMessageId, String content,
                                     String token) {
        ApiResponse<SendMessageResponse> response = post(
                "/api/v1/conversations/" + conversationId + "/messages",
                Map.of("conversationId", conversationId, "clientMessageId", clientMessageId,
                        "type", "TEXT", "content", content),
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

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
