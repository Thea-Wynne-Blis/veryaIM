package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.MessagePageResponse;
import com.veryal.access.http.dto.SendMessageResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.access.http.dto.ConversationSearchResponse;
import com.veryal.access.http.dto.SearchResponse;
import com.veryal.application.search.ConversationSearchHit;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.MessageType;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.StoredFile;
import com.veryal.domain.file.StorageType;
import com.veryal.domain.file.FileStatus;
import com.veryal.domain.conversation.ConversationRepository;
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
import java.time.Instant;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话内消息搜索：文本匹配、通配符转义、非成员 403、撤回消息排除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MessageSearchIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired private MessageRepository messages;
    @Autowired private FileRepository files;
    @Autowired private IdGenerator ids;
    @Autowired private ConversationRepository conversations;

    @Test
    void unifiedSearchPagesFiltersAndLocatesOldAttachmentsByStoredFilename() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("un_alice_" + suffix);
        UserResponse bob = register("un_bob_" + suffix);
        register("un_out_" + suffix);
        String token = login("un_alice_" + suffix, "un-a-" + suffix).token();
        String outsiderToken = login("un_out_" + suffix, "un-o-" + suffix).token();
        String conversationId = createConversation(bob, token);
        Instant day = Instant.parse("2026-11-17T02:00:00Z");
        long oldImage = ids.nextId();
        files.save(new StoredFile(oldImage, alice.userId(), "search/" + oldImage,
                "设计方案.PNG", "image/png", 100, "hash-" + oldImage,
                StorageType.LOCAL, FileStatus.ACTIVE, day, null, conversationId));
        long targetId = ids.nextId();
        // 图片消息本身只有 fileId，没有文件名。再放入 405 条新附件，覆盖过去的 300 条上限。
        seed(targetId, conversationId, alice.userId(), 1, MessageType.IMAGE,
                "{\"fileId\":" + oldImage + "}", day);
        long otherFile = ids.nextId();
        files.save(new StoredFile(otherFile, bob.userId(), "search/" + otherFile,
                "other.txt", "text/plain", 8, "hash-" + otherFile,
                StorageType.LOCAL, FileStatus.ACTIVE, day, null, conversationId));
        for (int seq = 2; seq <= 406; seq++) {
            seed(ids.nextId(), conversationId, bob.userId(), seq, MessageType.FILE,
                    "{\"fileId\":" + otherFile + "}", day.plusSeconds(seq));
        }
        seed(ids.nextId(), conversationId, alice.userId(), 407, MessageType.TEXT,
                "发布 100% 完成 HTTPS://example.com", day.plusSeconds(407));
        long recalled = ids.nextId();
        seed(recalled, conversationId, alice.userId(), 408, MessageType.TEXT, "已撤回内容", day);
        messages.markRecalled(recalled, alice.userId(), day.plusSeconds(1));

        ConversationSearchResponse first = unified(conversationId, "limit=2", token, 200);
        assertThat(first.messages()).extracting(ConversationSearchHit::seq).containsExactly(407L, 406L);
        assertThat(first.hasMore()).isTrue();
        ConversationSearchResponse next = unified(conversationId,
                "limit=2&cursor=" + first.nextCursor(), token, 200);
        assertThat(next.messages()).extracting(ConversationSearchHit::seq).containsExactly(405L, 404L);
        assertThat(unified(conversationId, "category=MEDIA&keyword=设计方案", token, 200).messages())
                .singleElement().satisfies(hit -> {
                    assertThat(hit.fileName()).isEqualTo("设计方案.PNG");
                    assertThat(hit.messageId()).isEqualTo(targetId);
                });
        // ALL 分类也必须跨多个候选批次，找到旧图片。
        assertThat(unified(conversationId, "keyword=设计方案", token, 200).messages())
                .extracting(ConversationSearchHit::messageId).containsExactly(targetId);
        var global = rest.exchange(java.net.URI.create("/api/v1/search?keyword=设计方案.png"),
                HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<ApiResponse<SearchResponse>>() {});
        assertThat(global.getStatusCode().value()).isEqualTo(200);
        assertThat(global.getBody().data().files()).singleElement()
                .satisfies(file -> assertThat(file.messageId()).isEqualTo(targetId));
        assertThat(unified(conversationId, "category=LINK", token, 200).messages()).hasSize(1);
        assertThat(unified(conversationId, "category=TEXT&keyword=HtTpS", token, 200).messages()).hasSize(1);
        assertThat(unified(conversationId, "category=TEXT&keyword=%25", token, 200).messages()).hasSize(1);
        assertThat(unified(conversationId, "category=TEXT&keyword=_", token, 200).messages()).isEmpty();
        assertThat(unified(conversationId, "senderId=" + alice.userId()
                + "&from=2026-11-17T02:00:00Z&to=2026-11-17T02:00:01Z", token, 200).messages())
                .extracting(ConversationSearchHit::messageId).containsExactly(targetId);
        assertThat(unified(conversationId, "category=FILE&senderId=" + alice.userId(), token, 200)
                .messages()).isEmpty();
        unified(conversationId, "category=INVALID", token, 400);
        unified(conversationId, "from=2026-11-18T00:00:00Z&to=2026-11-17T00:00:00Z", token, 400);
        unified(conversationId, "", outsiderToken, 403);

        var context = rest.exchange("/api/v1/conversations/" + conversationId + "/messages/"
                        + targetId + "/context", HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<ApiResponse<MessagePageResponse>>() {});
        assertThat(context.getStatusCode().value()).isEqualTo(200);
        assertThat(context.getBody().data().messages()).anyMatch(message -> message.messageId() == targetId);
        var forbidden = rest.exchange("/api/v1/conversations/" + conversationId + "/messages/"
                        + targetId + "/context", HttpMethod.GET, new HttpEntity<>(headers(outsiderToken)),
                new ParameterizedTypeReference<ApiResponse<MessagePageResponse>>() {});
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        conversations.clearMemberHistory(conversationId, alice.userId(), 406, day.plusSeconds(500));
        assertThat(unified(conversationId, "", token, 200).messages())
                .extracting(ConversationSearchHit::seq).containsExactly(407L);
        var clearedContext = rest.exchange("/api/v1/conversations/" + conversationId + "/messages/"
                        + targetId + "/context", HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<ApiResponse<MessagePageResponse>>() {});
        assertThat(clearedContext.getStatusCode().value()).isEqualTo(404);
    }

    private void seed(long id, String conversationId, long senderId, long seq,
                      MessageType type, String content, Instant time) {
        messages.append(Message.create(id, conversationId, senderId, "search-" + id, seq, type,
                content.getBytes(StandardCharsets.UTF_8), time));
    }

    private ConversationSearchResponse unified(String conversationId, String query, String token, int status) {
        var response = rest.exchange(java.net.URI.create("/api/v1/conversations/" + conversationId + "/search?" + query),
                HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<ApiResponse<ConversationSearchResponse>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        return response.getBody().data();
    }

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
