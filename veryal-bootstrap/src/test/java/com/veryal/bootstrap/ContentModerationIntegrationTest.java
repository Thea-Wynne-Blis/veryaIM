package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.PlatformAuthResponse;
import com.veryal.access.http.dto.SensitiveWordResponse;
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
 * 敏感词 DFA：运营添加词库后，消息发送被拦截。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContentModerationIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void sensitiveWordBlocksMessage() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("cm_owner_" + suffix);
        UserResponse member = register("cm_member_" + suffix);
        AuthResponse ownerLogin = login("cm_owner_" + suffix);
        GroupResponse group = post("/api/v1/groups",
                Map.of("name", "Moderation " + suffix, "memberIds", List.of(member.userId())),
                ownerLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();

        String adminToken = platformLogin();
        SensitiveWordResponse word = post("/api/v1/platform/ops/sensitive-words",
                Map.of("word", "badword"), adminToken,
                new ParameterizedTypeReference<ApiResponse<SensitiveWordResponse>>() {
                }).data();
        assertThat(word.word()).isEqualTo("badword");

        assertThat(sendStatus(group.groupId(), "badword", ownerLogin.token())).isEqualTo(400);
        assertThat(sendStatus(group.groupId(), "clean text", ownerLogin.token())).isEqualTo(200);

        ApiResponse<List<SensitiveWordResponse>> wordsResponse = get(
                "/api/v1/platform/ops/sensitive-words", adminToken,
                new ParameterizedTypeReference<ApiResponse<List<SensitiveWordResponse>>>() {
                });
        List<SensitiveWordResponse> words = wordsResponse.data();
        assertThat(words).extracting(SensitiveWordResponse::word).contains("badword");

        rest.exchange("/api/v1/platform/ops/sensitive-words/" + word.id(), HttpMethod.DELETE,
                new HttpEntity<>(headers(adminToken)), new ParameterizedTypeReference<>() {
                });
        assertThat(sendStatus(group.groupId(), "badword", ownerLogin.token())).isEqualTo(200);
    }

    private int sendStatus(String groupId, String content, String token) {
        return rest.exchange("/api/v1/conversations/" + groupId + "/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("conversationId", groupId,
                        "clientMessageId", "cm-" + System.nanoTime(), "type", "TEXT",
                        "content", content), headers(token)),
                new ParameterizedTypeReference<>() {
                }).getStatusCode().value();
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", "cm-" + username, "platform", "LINUX", "deviceName", "test"), null,
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                }).data();
    }

    private String platformLogin() {
        return post("/api/v1/platform/auth/login",
                Map.of("username", "platform_admin", "password", "admin12345"), null,
                new ParameterizedTypeReference<ApiResponse<PlatformAuthResponse>>() {
                }).data().token();
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
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
