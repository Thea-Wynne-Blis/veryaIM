package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.ConversationCardResponse;
import com.veryal.access.http.dto.ConversationResponse;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话列表的成员级设置：置顶、免打扰、手动未读、不显示、删除聊天记录。
 *
 * <p>核心约束：这些设置只作用于操作者本人。清空记录只是抬高自己的可见水位，
 * 会话另一方的聊天记录必须完全不受影响。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConversationPrefsIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void memberPrefsOnlyAffectTheOwner() {
        String suffix = Long.toString(System.nanoTime());
        register("cp_alice_" + suffix);
        UserResponse bob = register("cp_bob_" + suffix);
        AuthResponse alice = login("cp_alice_" + suffix, "cp-a-" + suffix);
        AuthResponse bobLogin = login("cp_bob_" + suffix, "cp-b-" + suffix);

        String conversationId = createConversation(bob, alice.token());
        send(conversationId, "cp-1-" + suffix, "第一条", alice.token());
        send(conversationId, "cp-2-" + suffix, "第二条", alice.token());
        send(conversationId, "cp-3-" + suffix, "第三条", alice.token());
        assertThat(cardOf(conversationId, bobLogin.token()).unreadCount()).isEqualTo(3);

        // 置顶：只影响自己
        prefs(conversationId, Map.of("pinned", true), alice.token());
        assertThat(cardOf(conversationId, alice.token()).pinned()).isTrue();
        assertThat(cardOf(conversationId, bobLogin.token()).pinned()).isFalse();

        // 免打扰：只影响自己
        prefs(conversationId, Map.of("muted", true), alice.token());
        assertThat(cardOf(conversationId, alice.token()).muted()).isTrue();
        assertThat(cardOf(conversationId, bobLogin.token()).muted()).isFalse();

        // 手动未读：自己发的消息本来已读，标为未读后至少 1 条
        assertThat(cardOf(conversationId, alice.token()).unreadCount()).isZero();
        prefs(conversationId, Map.of("unread", true), alice.token());
        assertThat(cardOf(conversationId, alice.token()).unreadCount()).isEqualTo(1);

        // 打开会话（推进读游标）会清掉手动未读
        markRead(conversationId, 3, alice.token());
        assertThat(cardOf(conversationId, alice.token()).unreadCount()).isZero();

        // 不显示：自己看不到，对方照常
        prefs(conversationId, Map.of("hidden", true), bobLogin.token());
        assertThat(cardOf(conversationId, bobLogin.token()).hidden()).isTrue();
        assertThat(cardOf(conversationId, alice.token()).hidden()).isFalse();

        // 新消息到达（比「不显示」的时间更晚）把会话重新顶回来
        send(conversationId, "cp-4-" + suffix, "第四条", alice.token());
        ConversationCardResponse revived = cardOf(conversationId, bobLogin.token());
        assertThat(revived.hidden()).isFalse();
        assertThat(revived.unreadCount()).isEqualTo(4);

        // 删除聊天记录：只清空自己这一侧
        prefs(conversationId, Map.of("pinned", true), bobLogin.token());
        clear(conversationId, bobLogin.token());
        ConversationCardResponse cleared = cardOf(conversationId, bobLogin.token());
        assertThat(cleared.hidden()).isTrue();
        assertThat(cleared.pinned()).isFalse();
        assertThat(cleared.unreadCount()).isZero();
        assertThat(history(conversationId, bobLogin.token()).data().messages()).isEmpty();

        // 对方记录完好
        List<?> peerMessages = history(conversationId, alice.token()).data().messages();
        assertThat(peerMessages).hasSize(4);

        // 清空之后再来的消息仍可正常看到
        send(conversationId, "cp-5-" + suffix, "第五条", alice.token());
        ConversationCardResponse afterClear = cardOf(conversationId, bobLogin.token());
        assertThat(afterClear.hidden()).isFalse();
        assertThat(afterClear.unreadCount()).isEqualTo(1);
        MessagePageResponse visible = history(conversationId, bobLogin.token()).data();
        assertThat(visible.messages()).hasSize(1);
        assertThat(visible.messages().get(0).content()).isEqualTo("第五条");
    }

    private ConversationCardResponse cardOf(String conversationId, String token) {
        ResponseEntity<ApiResponse<List<ConversationCardResponse>>> response = rest.exchange(
                "/api/v1/conversations/cards", HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data().stream()
                .filter(card -> card.conversationId().equals(conversationId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("card not found: " + conversationId));
    }

    private void prefs(String conversationId, Map<String, Object> body, String token) {
        ResponseEntity<ApiResponse<Void>> response = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/prefs", HttpMethod.PUT,
                new HttpEntity<>(body, headers(token)), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private void clear(String conversationId, String token) {
        ResponseEntity<ApiResponse<Void>> response = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/clear", HttpMethod.POST,
                new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private void markRead(String conversationId, long seq, String token) {
        ResponseEntity<ApiResponse<Void>> response = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/read", HttpMethod.POST,
                new HttpEntity<>(Map.of("readSequence", seq), headers(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private ApiResponse<MessagePageResponse> history(String conversationId, String token) {
        ResponseEntity<ApiResponse<MessagePageResponse>> response = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages?cursor=0&limit=50",
                HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
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
        ApiResponse<ConversationResponse> response = post("/api/v1/conversations",
                Map.of("peerUserId", peer.userId()), token,
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
