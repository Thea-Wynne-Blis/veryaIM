package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.ConversationFileResponse;
import com.veryal.access.http.dto.FavoriteResponse;
import com.veryal.access.http.dto.FileResponse;
import com.veryal.access.http.dto.MessageResponse;
import com.veryal.access.http.dto.SearchResponse;
import com.veryal.access.http.dto.SendMessageResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话文件、全局搜索与收藏：三者都依赖「我参与的会话」权限边界。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FavoriteSearchIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void listsConversationFilesAndSearchesAcrossConversations() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("fs_alice_" + suffix);
        UserResponse bob = register("fs_bob_" + suffix);
        AuthResponse aliceLogin = login("fs_alice_" + suffix, "fs-a-" + suffix);
        AuthResponse bobLogin = login("fs_bob_" + suffix, "fs-b-" + suffix);

        String conversationId = createConversation(bob, aliceLogin.token());
        String text = "hello favorite " + suffix;
        sendText(conversationId, "fs-t-" + suffix, text, aliceLogin.token());
        String filename = "notes-" + suffix + ".txt";
        FileResponse uploaded = upload(filename, aliceLogin.token(), conversationId);
        sendFile(conversationId, "fs-f-" + suffix, uploaded.fileId(), filename, aliceLogin.token());

        List<ConversationFileResponse> files = getList(
                "/api/v1/conversations/" + conversationId + "/files",
                aliceLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(files).hasSize(1);
        assertThat(files.get(0).fileId()).isEqualTo(uploaded.fileId());
        assertThat(files.get(0).name()).isEqualTo(filename);
        assertThat(files.get(0).senderId()).isEqualTo(alice.userId());

        SearchResponse byText = search("favorite", bobLogin.token());
        assertThat(byText.messages()).anyMatch(hit -> text.equals(hit.text())
                && conversationId.equals(hit.conversationId()));

        SearchResponse byFile = search("notes-" + suffix, bobLogin.token());
        assertThat(byFile.files()).anyMatch(hit -> filename.equals(hit.name())
                && uploaded.fileId() == hit.fileId());

        // 陌生人不能看别人的会话文件。
        UserResponse outsider = register("fs_out_" + suffix);
        AuthResponse outsiderLogin = login("fs_out_" + suffix, "fs-c-" + suffix);
        ResponseEntity<ApiResponse<List<ConversationFileResponse>>> forbidden = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/files",
                HttpMethod.GET, new HttpEntity<>(headers(outsiderLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(outsider.userId()).isPositive();
    }

    @Test
    void favoritesAreSnapshotsAndSurviveRecall() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("fv_alice_" + suffix);
        UserResponse bob = register("fv_bob_" + suffix);
        AuthResponse aliceLogin = login("fv_alice_" + suffix, "fv-a-" + suffix);
        AuthResponse bobLogin = login("fv_bob_" + suffix, "fv-b-" + suffix);

        String conversationId = createConversation(bob, aliceLogin.token());
        String text = "keep me " + suffix;
        SendMessageResponse sent = sendText(conversationId, "fv-t-" + suffix, text,
                aliceLogin.token());

        ApiResponse<FavoriteResponse> added = post("/api/v1/favorites",
                Map.of("conversationId", conversationId, "messageId", sent.message().messageId()),
                bobLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(added.data().text()).isEqualTo(text);
        assertThat(added.data().conversationTitle()).isNotBlank();

        // 重复收藏是幂等的。
        ApiResponse<FavoriteResponse> again = post("/api/v1/favorites",
                Map.of("conversationId", conversationId, "messageId", sent.message().messageId()),
                bobLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(again.data().id()).isEqualTo(added.data().id());

        List<FavoriteResponse> list = getList("/api/v1/favorites", bobLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(list).hasSize(1);

        // 原消息被撤回后，收藏里的快照仍然可读。
        recall(conversationId, sent.message().messageId(), aliceLogin.token());
        List<FavoriteResponse> afterRecall = getList("/api/v1/favorites", bobLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(afterRecall).hasSize(1);
        assertThat(afterRecall.get(0).text()).isEqualTo(text);

        // 别人的收藏删不掉，自己的可以。
        ResponseEntity<ApiResponse<Void>> wrongOwner = rest.exchange(
                "/api/v1/favorites/" + added.data().id(), HttpMethod.DELETE,
                new HttpEntity<>(headers(aliceLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(wrongOwner.getStatusCode().value()).isEqualTo(404);

        delete("/api/v1/favorites/" + added.data().id(), bobLogin.token());
        assertThat(getList("/api/v1/favorites", bobLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<FavoriteResponse>>>() {
                })).isEmpty();
    }

    private SearchResponse search(String keyword, String token) {
        ApiResponse<SearchResponse> response = get(
                "/api/v1/search?keyword=" + java.net.URLEncoder.encode(keyword,
                        StandardCharsets.UTF_8),
                token, new ParameterizedTypeReference<>() {
                });
        return response.data();
    }

    private SendMessageResponse sendText(String conversationId, String clientMessageId,
                                         String content, String token) {
        return post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("conversationId", conversationId, "clientMessageId", clientMessageId,
                        "type", "TEXT", "content", content),
                token, new ParameterizedTypeReference<ApiResponse<SendMessageResponse>>() {
                }).data();
    }

    private void sendFile(String conversationId, String clientMessageId, long fileId,
                          String filename, String token) {
        String content = "{\"fileId\":" + fileId + ",\"fileName\":\"" + filename + "\"}";
        post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("conversationId", conversationId, "clientMessageId", clientMessageId,
                        "type", "FILE", "content", content),
                token, new ParameterizedTypeReference<ApiResponse<SendMessageResponse>>() {
                });
    }

    private FileResponse upload(String filename, String token, String conversationId) {
        ByteArrayResource resource = new ByteArrayResource("veryal notes".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        body.add("conversationId", conversationId);
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<ApiResponse<FileResponse>> response = rest.exchange("/api/v1/files",
                HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private void recall(String conversationId, long messageId, String token) {
        ResponseEntity<ApiResponse<MessageResponse>> response = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages/" + messageId + "/recall",
                HttpMethod.POST, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
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

    private <T> List<T> getList(String path, String token,
                                ParameterizedTypeReference<ApiResponse<List<T>>> type) {
        ApiResponse<List<T>> response = get(path, token, type);
        return response.data();
    }

    private void delete(String path, String token) {
        ResponseEntity<ApiResponse<Void>> response = rest.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
