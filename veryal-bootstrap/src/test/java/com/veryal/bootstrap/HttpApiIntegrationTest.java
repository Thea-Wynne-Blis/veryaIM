package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.ConversationResponse;
import com.veryal.access.http.dto.MessagePageResponse;
import com.veryal.access.http.dto.SendMessageRequest;
import com.veryal.access.http.dto.SendMessageResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.ErrorCode;
import com.veryal.infrastructure.mysql.mapper.OutboxMapper;
import com.veryal.infrastructure.mysql.po.OutboxPO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
 * HTTP API 端到端测试：注册 → 登录 → 会话 → 消息 → 幂等 → 历史 → 同步 → 已读。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OutboxMapper outboxMapper;

    @Test
    void completeMessagingFlow() {
        String suffix = Long.toString(System.nanoTime());
        String aliceName = "alice_" + suffix;
        String bobName = "bob_" + suffix;
        String charlieName = "charlie_" + suffix;

        UserResponse alice = register(aliceName);
        UserResponse bob = register(bobName);
        register(charlieName);

        AuthResponse aliceLogin = login(aliceName, "device-a-" + suffix);
        AuthResponse bobLogin = login(bobName, "device-b-" + suffix);
        AuthResponse charlieLogin = login(charlieName, "device-c-" + suffix);
        assertThat(aliceLogin.token()).isNotBlank();
        assertThat(aliceLogin.user().userId()).isEqualTo(alice.userId());

        ConversationResponse conversation = post("/api/v1/conversations",
                Map.of("peerUserId", bob.userId()), aliceLogin.token(),
                new ParameterizedTypeReference<ApiResponse<ConversationResponse>>() {
                }).data();
        String conversationId = conversation.conversationId();
        assertThat(conversationId).startsWith("p_");

        SendMessageResponse first = sendMessage(conversationId, "client-msg-1", "hello bob", aliceLogin.token());
        assertThat(first.duplicate()).isFalse();
        assertThat(first.message().sequence()).isEqualTo(1);
        assertThat(first.message().content()).isEqualTo("hello bob");

        SendMessageResponse duplicate = sendMessage(conversationId, "client-msg-1", "hello bob", aliceLogin.token());
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.message().messageId()).isEqualTo(first.message().messageId());
        assertThat(duplicate.message().sequence()).isEqualTo(1);

        SendMessageResponse second = sendMessage(conversationId, "client-msg-2", "second", aliceLogin.token());
        assertThat(second.message().sequence()).isEqualTo(2);

        // 每条新消息在同一事务内写入一条 Outbox 事件；幂等重发不新增事件
        Long outboxCount = outboxMapper.selectCount(Wrappers.<OutboxPO>lambdaQuery()
                .eq(OutboxPO::getAggregateId, conversationId));
        assertThat(outboxCount).isEqualTo(2L);
        OutboxPO firstEvent = outboxMapper.selectOne(Wrappers.<OutboxPO>lambdaQuery()
                .eq(OutboxPO::getAggregateId, conversationId)
                .eq(OutboxPO::getEventType, "message.private.created")
                .orderByAsc(OutboxPO::getId)
                .last("LIMIT 1"));
        assertThat(firstEvent).isNotNull();
        assertThat(firstEvent.status).isZero();
        assertThat(firstEvent.payload).isNotEmpty();

        ApiResponse<MessagePageResponse> history = get(
                "/api/v1/conversations/" + conversationId + "/messages?cursor=0&limit=10", aliceLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(history.data().messages()).hasSize(2);
        assertThat(history.data().messages()).extracting("sequence").containsExactly(1L, 2L);
        assertThat(history.data().hasMore()).isFalse();

        ApiResponse<MessagePageResponse> syncFromZero = get(
                "/api/v1/sync/messages?conversationId=" + conversationId + "&cursor=0&limit=10", bobLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(syncFromZero.data().messages()).hasSize(2);
        assertThat(syncFromZero.data().nextCursor()).isEqualTo("2");

        ApiResponse<MessagePageResponse> syncFromTwo = get(
                "/api/v1/sync/messages?conversationId=" + conversationId + "&cursor=2&limit=10", bobLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(syncFromTwo.data().messages()).isEmpty();
        assertThat(syncFromTwo.data().hasMore()).isFalse();

        ApiResponse<Void> read = post("/api/v1/conversations/" + conversationId + "/read",
                Map.of("readSequence", 2), bobLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(read.successful()).isTrue();

        ApiResponse<List<ConversationResponse>> conversations = get("/api/v1/conversations", bobLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        ConversationResponse summary = conversations.data().stream()
                .filter(item -> item.conversationId().equals(conversationId))
                .findFirst()
                .orElseThrow();
        assertThat(summary.lastReadSeq()).isEqualTo(2);
        assertThat(summary.lastSeq()).isEqualTo(2);

        // 非成员不能读取会话消息
        HttpHeaders charlieHeaders = jsonHeaders(charlieLogin.token());
        ResponseEntity<ApiResponse<MessagePageResponse>> forbidden = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages", HttpMethod.GET,
                new HttpEntity<>(charlieHeaders), new ParameterizedTypeReference<>() {
                });
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getBody()).isNotNull();
        assertThat(forbidden.getBody().code()).isEqualTo(ErrorCode.NOT_CONVERSATION_MEMBER.code());

        // 无 Token 访问
        ResponseEntity<ApiResponse<Void>> unauthorized = rest.exchange("/api/v1/users/me", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)), new ParameterizedTypeReference<>() {
                });
        assertThat(unauthorized.getStatusCode().value()).isEqualTo(401);
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
                        "platform", "LINUX", "deviceName", "test-client"),
                null, new ParameterizedTypeReference<>() {
                });
        assertThat(response.successful()).isTrue();
        return response.data();
    }

    private SendMessageResponse sendMessage(String conversationId, String clientMessageId, String content,
                                            String token) {
        ApiResponse<SendMessageResponse> response = post("/api/v1/conversations/" + conversationId + "/messages",
                new SendMessageRequest(conversationId, clientMessageId, "TEXT", content, null), token,
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.successful()).isTrue();
        return response.data();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private <T> ApiResponse<T> get(String path, String token, ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
