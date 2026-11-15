package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.TenantResponse;
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
 * 多租户：平台开通、租户码登录、同名用户隔离、跨租户访问拒绝、状态与配额。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MultiTenantIntegrationTest {

    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void tenantsAreIsolatedAndResolvedByCode() {
        String suffix = Long.toString(System.nanoTime());
        String code = "t" + suffix.substring(Math.max(0, suffix.length() - 8));
        TenantResponse tenant = createTenant(code, "Tenant " + suffix, 10, 5, 1024 * 1024);

        UserResponse defaultAlice = register("tenant_alice_" + suffix, null);
        UserResponse tenantAlice = register("tenant_alice_" + suffix, code);
        assertThat(defaultAlice.userId()).isNotEqualTo(tenantAlice.userId());

        AuthResponse defaultLogin = login("tenant_alice_" + suffix, "td-" + suffix, null);
        AuthResponse tenantLogin = login("tenant_alice_" + suffix, "tt-" + suffix, code);
        assertThat(defaultLogin.token()).isNotEqualTo(tenantLogin.token());

        // 默认租户用户不能把其他租户用户加进私聊
        ResponseEntity<ApiResponse<com.veryal.access.http.dto.ConversationResponse>> crossTenant =
                rest.exchange("/api/v1/conversations", HttpMethod.POST,
                        new HttpEntity<>(Map.of("peerUserId", tenantAlice.userId()),
                                headers(defaultLogin.token())),
                        new ParameterizedTypeReference<>() {
                        });
        assertThat(crossTenant.getStatusCode().value()).isEqualTo(404);
        assertThat(tenant.tenantId()).isPositive();
    }

    @Test
    void quotaAndDisabledTenantAreEnforced() {
        String suffix = Long.toString(System.nanoTime());
        String limitedCode = "q" + suffix.substring(Math.max(0, suffix.length() - 8));
        createTenant(limitedCode, "Quota " + suffix, 1, 1, 0);

        register("quota_one_" + suffix, limitedCode);
        ResponseEntity<ApiResponse<UserResponse>> second = rest.exchange("/api/v1/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "quota_two_" + suffix,
                        "password", "password123", "nickname", "quota2",
                        "tenantCode", limitedCode), jsonHeaders()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(second.getStatusCode().value()).isEqualTo(403);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().code()).isEqualTo(40304);

        String disabledCode = "d" + suffix.substring(Math.max(0, suffix.length() - 8));
        TenantResponse disabled = createTenant(disabledCode, "Disabled " + suffix, 10, 10, 0);
        ResponseEntity<ApiResponse<TenantResponse>> disable = rest.exchange(
                "/api/v1/platform/tenants/" + disabled.tenantId() + "/disable", HttpMethod.POST,
                new HttpEntity<>(adminHeaders()), new ParameterizedTypeReference<>() {
                });
        assertThat(disable.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<ApiResponse<AuthResponse>> login = rest.exchange("/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "nobody", "password", "password123",
                        "deviceId", "d-" + suffix, "tenantCode", disabledCode), jsonHeaders()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(login.getStatusCode().value()).isEqualTo(403);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().code()).isEqualTo(40303);
    }

    private TenantResponse createTenant(String code, String name, int maxUsers, int maxGroups,
                                        long maxStorage) {
        ResponseEntity<ApiResponse<TenantResponse>> response = rest.exchange(
                "/api/v1/platform/tenants", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", code, "name", name, "maxUsers", maxUsers,
                        "maxGroups", maxGroups, "maxStorageBytes", maxStorage), adminHeaders()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private UserResponse register(String username, String tenantCode) {
        Map<String, Object> body = tenantCode == null
                ? Map.of("username", username, "password", "password123", "nickname", username)
                : Map.of("username", username, "password", "password123", "nickname", username,
                "tenantCode", tenantCode);
        ResponseEntity<ApiResponse<UserResponse>> response = rest.exchange("/api/v1/auth/register",
                HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private AuthResponse login(String username, String deviceId, String tenantCode) {
        Map<String, Object> body = tenantCode == null
                ? Map.of("username", username, "password", "password123", "deviceId", deviceId,
                "platform", "LINUX", "deviceName", "test")
                : Map.of("username", username, "password", "password123", "deviceId", deviceId,
                "platform", "LINUX", "deviceName", "test", "tenantCode", tenantCode);
        ResponseEntity<ApiResponse<AuthResponse>> response = rest.exchange("/api/v1/auth/login",
                HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Admin-Token", ADMIN_TOKEN);
        return headers;
    }
}
