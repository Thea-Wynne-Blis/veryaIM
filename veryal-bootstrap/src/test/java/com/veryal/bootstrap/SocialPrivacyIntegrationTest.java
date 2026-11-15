package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.ReportResponse;
import com.veryal.access.http.dto.UserPrivacyResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.access.http.dto.UserSearchResponse;
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
 * 消费级社交：用户搜索、隐私设置、好友策略与举报。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SocialPrivacyIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void searchPrivacyFriendPolicyAndReport() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("sp_alice_" + suffix);
        UserResponse bob = register("sp_bob_" + suffix);
        AuthResponse aliceLogin = login("sp_alice_" + suffix);
        AuthResponse bobLogin = login("sp_bob_" + suffix);

        ApiResponse<List<UserSearchResponse>> foundResponse = get(
                "/api/v1/users/search?keyword=sp_bob_" + suffix, aliceLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<UserSearchResponse>>>() {
                });
        List<UserSearchResponse> found = foundResponse.data();
        assertThat(found).extracting(UserSearchResponse::userId).contains(bob.userId());

        // 关闭用户名搜索
        put("/api/v1/users/me/privacy", Map.of("searchByUsername", false), bobLogin.token(),
                new ParameterizedTypeReference<ApiResponse<UserPrivacyResponse>>() {
                });
        ApiResponse<List<UserSearchResponse>> hiddenResponse = get(
                "/api/v1/users/search?keyword=sp_bob_" + suffix, aliceLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<UserSearchResponse>>>() {
                });
        List<UserSearchResponse> hidden = hiddenResponse.data();
        assertThat(hidden).extracting(UserSearchResponse::userId).doesNotContain(bob.userId());

        // 拒绝陌生人好友申请
        put("/api/v1/users/me/privacy", Map.of("friendPolicy", "REJECT"), bobLogin.token(),
                new ParameterizedTypeReference<ApiResponse<UserPrivacyResponse>>() {
                });
        assertThat(postStatus("/api/v1/friends/requests",
                Map.of("toUserId", bob.userId(), "message", "hi"), aliceLogin.token()))
                .isEqualTo(403);

        // 允许申请
        put("/api/v1/users/me/privacy", Map.of("friendPolicy", "VERIFY"), bobLogin.token(),
                new ParameterizedTypeReference<ApiResponse<UserPrivacyResponse>>() {
                });
        assertThat(postStatus("/api/v1/friends/requests",
                Map.of("toUserId", bob.userId(), "message", "hi"), aliceLogin.token()))
                .isEqualTo(200);

        // 举报用户
        ApiResponse<ReportResponse> report = post("/api/v1/reports",
                Map.of("targetType", "USER", "targetId", Long.toString(bob.userId()),
                        "reason", "spam", "detail", "test report"), aliceLogin.token(),
                new ParameterizedTypeReference<ApiResponse<ReportResponse>>() {
                });
        assertThat(report.data().status()).isEqualTo("PENDING");
        ApiResponse<List<ReportResponse>> mineResponse = get("/api/v1/reports/me",
                aliceLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<ReportResponse>>>() {
                });
        List<ReportResponse> mine = mineResponse.data();
        assertThat(mine).extracting(ReportResponse::reportId).contains(report.data().reportId());

        // 不能举报自己
        assertThat(postStatus("/api/v1/reports",
                Map.of("targetType", "USER", "targetId", Long.toString(alice.userId()),
                        "reason", "self"), aliceLogin.token())).isEqualTo(400);
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", "sp-" + username, "platform", "LINUX", "deviceName", "test"), null,
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                }).data();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private <T> ApiResponse<T> put(String path, Object body, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.PUT,
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

    private int postStatus(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)),
                new ParameterizedTypeReference<>() {
                }).getStatusCode().value();
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
