package com.veryal.bootstrap;

import com.veryal.access.http.dto.PlatformAdminResponse;
import com.veryal.access.http.dto.PlatformAuditResponse;
import com.veryal.access.http.dto.PlatformAuthResponse;
import com.veryal.access.http.dto.TenantResponse;
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
 * 平台管理员 CRUD、角色鉴权、密码重置与平台审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlatformAdminManagementIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void platformAdminLifecycleAndAudit() {
        String suffix = Long.toString(System.nanoTime());
        String adminToken = platformLogin("platform_admin", "admin12345").token();
        String supportName = "support_" + suffix.substring(Math.max(0, suffix.length() - 8));

        PlatformAdminResponse support = post("/api/v1/platform/admins",
                Map.of("username", supportName, "password", "support123",
                        "displayName", "Support", "role", "SUPPORT"), adminToken,
                new ParameterizedTypeReference<ApiResponse<PlatformAdminResponse>>() {
                }).data();
        assertThat(support.role()).isEqualTo("SUPPORT");

        PlatformAuthResponse supportLogin = platformLogin(supportName, "support123");
        assertThat(supportLogin.role()).isEqualTo("SUPPORT");

        // SUPPORT 可以查看租户列表，但不能创建租户或管理管理员
        assertThat(get("/api/v1/platform/tenants", supportLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<TenantResponse>>>() {
                }).successful()).isTrue();
        assertThat(postStatus("/api/v1/platform/tenants", Map.of("code", "deny" + suffix.substring(
                        Math.max(0, suffix.length() - 6)), "name", "Denied", "maxUsers", 1,
                        "maxGroups", 1, "maxStorageBytes", 0), supportLogin.token())).isEqualTo(403);
        assertThat(getStatus("/api/v1/platform/admins", supportLogin.token())).isEqualTo(403);

        // 重置密码后旧密码失效
        post("/api/v1/platform/admins/" + support.adminId() + "/password",
                Map.of("password", "newpass123"), adminToken,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(platformLoginStatus(supportName, "support123")).isEqualTo(401);
        assertThat(platformLogin(supportName, "newpass123").token()).isNotBlank();

        // 停用后无法登录
        post("/api/v1/platform/admins/" + support.adminId() + "/disable", Map.of(), adminToken,
                new ParameterizedTypeReference<ApiResponse<PlatformAdminResponse>>() {
                });
        assertThat(platformLoginStatus(supportName, "newpass123")).isEqualTo(403);

        // 自我停用被拒绝
        long selfId = platformLogin("platform_admin", "admin12345").adminId();
        assertThat(postStatus("/api/v1/platform/admins/" + selfId + "/disable", Map.of(),
                adminToken)).isEqualTo(400);

        ApiResponse<List<PlatformAuditResponse>> audit = get("/api/v1/platform/audit-logs",
                adminToken, new ParameterizedTypeReference<>() {
                });
        assertThat(audit.data()).extracting(PlatformAuditResponse::action)
                .contains("ADMIN_CREATE", "ADMIN_PASSWORD_RESET", "ADMIN_STATUS");
    }

    private PlatformAuthResponse platformLogin(String username, String password) {
        ResponseEntity<ApiResponse<PlatformAuthResponse>> response = rest.exchange(
                "/api/v1/platform/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", password), jsonHeaders()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private int platformLoginStatus(String username, String password) {
        ResponseEntity<ApiResponse<PlatformAuthResponse>> response = rest.exchange(
                "/api/v1/platform/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", password), jsonHeaders()),
                new ParameterizedTypeReference<>() {
                });
        return response.getStatusCode().value();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), type);
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

    private int getStatus(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {
                }).getStatusCode().value();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
