package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.ReadReceiptResponse;
import com.veryal.access.http.dto.SendMessageResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.infrastructure.mysql.mapper.OutboxMapper;
import com.veryal.infrastructure.mysql.po.OutboxPO;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 已读回执：读游标推进、回执查询与 Outbox 事件写入。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReadReceiptIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OutboxMapper outboxMapper;

    @Test
    void advancingReadCursorPublishesReceipt() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("rr_alice_" + suffix);
        UserResponse bob = register("rr_bob_" + suffix);
        AuthResponse aliceLogin = login("rr_alice_" + suffix, "rr-a-" + suffix);
        AuthResponse bobLogin = login("rr_bob_" + suffix, "rr-b-" + suffix);

        String conversationId = createConversation(bob, aliceLogin.token());
        SendMessageResponse first = send(conversationId, "rr-1-" + suffix, aliceLogin.token());
        SendMessageResponse second = send(conversationId, "rr-2-" + suffix, aliceLogin.token());

        markRead(conversationId, second.message().sequence(), bobLogin.token());
        // 游标后退不应产生新回执
        markRead(conversationId, first.message().sequence(), bobLogin.token());

        ApiResponse<List<ReadReceiptResponse>> receipts = get(
                "/api/v1/conversations/" + conversationId + "/read-receipts", aliceLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        ReadReceiptResponse bobReceipt = receipts.data().stream()
                .filter(item -> item.userId() == bob.userId()).findFirst().orElseThrow();
        assertThat(bobReceipt.readSequence()).isEqualTo(second.message().sequence());

        List<OutboxPO> events = outboxMapper.selectList(null).stream()
                .filter(item -> "message.read".equals(item.eventType))
                .filter(item -> item.aggregateId.equals(conversationId))
                .toList();
        assertThat(events).hasSize(1);
    }

    private void markRead(String conversationId, long sequence, String token) {
        ApiResponse<Void> response = post("/api/v1/conversations/" + conversationId + "/read",
                Map.of("readSequence", sequence), token, new ParameterizedTypeReference<>() {
                });
        assertThat(response.successful()).isTrue();
    }

    private SendMessageResponse send(String conversationId, String clientMessageId, String token) {
        ApiResponse<SendMessageResponse> response = post(
                "/api/v1/conversations/" + conversationId + "/messages",
                Map.of("conversationId", conversationId, "clientMessageId", clientMessageId,
                        "type", "TEXT", "content", "read receipt"),
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
