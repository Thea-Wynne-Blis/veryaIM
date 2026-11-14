package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupResponse;
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
 * 群生命周期测试：群主转让、解散群、权限边界。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GroupLifecycleIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void transferOwnershipAndDisbandGroup() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("gl_owner_" + suffix);
        UserResponse nextOwner = register("gl_next_" + suffix);
        UserResponse member = register("gl_member_" + suffix);

        AuthResponse ownerLogin = login("gl_owner_" + suffix, "glo-" + suffix);
        AuthResponse nextLogin = login("gl_next_" + suffix, "gln-" + suffix);
        AuthResponse memberLogin = login("gl_member_" + suffix, "glm-" + suffix);

        GroupResponse group = post("/api/v1/groups",
                Map.of("name", "lifecycle", "memberIds",
                        List.of(nextOwner.userId(), member.userId())),
                ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
        String groupId = group.groupId();

        // 普通成员不能转让群主
        ResponseEntity<ApiResponse<Void>> forbiddenTransfer = rest.exchange(
                "/api/v1/groups/" + groupId + "/transfer", HttpMethod.POST,
                new HttpEntity<>(Map.of("newOwnerId", member.userId()), headers(nextLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbiddenTransfer.getStatusCode().value()).isEqualTo(403);

        // 群主转让
        GroupResponse transferred = post("/api/v1/groups/" + groupId + "/transfer",
                Map.of("newOwnerId", nextOwner.userId()), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
        assertThat(transferred.ownerId()).isEqualTo(nextOwner.userId());

        // 原群主降为管理员，不能解散群
        ResponseEntity<ApiResponse<Void>> oldOwnerDisband = rest.exchange(
                "/api/v1/groups/" + groupId, HttpMethod.DELETE,
                new HttpEntity<>(headers(ownerLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(oldOwnerDisband.getStatusCode().value()).isEqualTo(403);

        // 新群主解散群
        ApiResponse<Void> disbanded = delete("/api/v1/groups/" + groupId, nextLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(disbanded.successful()).isTrue();

        ResponseEntity<ApiResponse<GroupResponse>> gone = rest.exchange("/api/v1/groups/" + groupId,
                HttpMethod.GET, new HttpEntity<>(headers(memberLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(gone.getStatusCode().value()).isEqualTo(404);

        // 解散后会话不可再发消息
        ResponseEntity<ApiResponse<Void>> sendAfterDisband = rest.exchange(
                "/api/v1/conversations/" + groupId + "/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("conversationId", groupId, "clientMessageId", "after",
                        "type", "TEXT", "content", "hello"), headers(memberLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(sendAfterDisband.getStatusCode().value()).isEqualTo(404);

        ApiResponse<List<com.veryal.access.http.dto.GroupSummaryResponse>> groups = get(
                "/api/v1/groups", memberLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(groups.data()).noneMatch(item -> item.groupId().equals(groupId));
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

    private <T> ApiResponse<T> delete(String path, String token,
                                      ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.DELETE,
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
