package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.PlatformAuthResponse;
import com.veryal.access.http.dto.TenantAdminResponse;
import com.veryal.access.http.dto.TenantResponse;
import com.veryal.access.http.dto.TenantUsageResponse;
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
 * 租户管理员：平台任命、租户内增删、用量/账单。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TenantAdminIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void appointTenantAdminAndReadUsage() {
        String suffix = Long.toString(System.nanoTime());
        String platformToken = platformLogin();
        String code = "ta" + suffix.substring(Math.max(0, suffix.length() - 8));
        TenantResponse tenant = post("/api/v1/platform/tenants",
                Map.of("code", code, "name", "Tenant Admin", "maxUsers", 10,
                        "maxGroups", 5, "maxStorageBytes", 0), platformToken,
                new ParameterizedTypeReference<ApiResponse<TenantResponse>>() {
                }).data();

        UserResponse adminUser = register("ta_admin_" + suffix, code);
        UserResponse memberUser = register("ta_member_" + suffix, code);
        AuthResponse adminLogin = login("ta_admin_" + suffix, code, "ta-a-" + suffix);
        AuthResponse memberLogin = login("ta_member_" + suffix, code, "ta-b-" + suffix);

        // 平台任命第一个租户管理员
        post("/api/v1/platform/tenants/" + tenant.tenantId() + "/admins/" + adminUser.userId(),
                Map.of(), platformToken, new ParameterizedTypeReference<ApiResponse<TenantAdminResponse>>() {
                });

        assertThat(getStatus("/api/v1/tenant/admins", memberLogin.token())).isEqualTo(403);

        ApiResponse<List<TenantAdminResponse>> admins = get("/api/v1/tenant/admins",
                adminLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(admins.data()).extracting(TenantAdminResponse::userId)
                .contains(adminUser.userId());

        post("/api/v1/tenant/admins", Map.of("userId", memberUser.userId()), adminLogin.token(),
                new ParameterizedTypeReference<ApiResponse<TenantAdminResponse>>() {
                });
        assertThat(getStatus("/api/v1/tenant/admins", memberLogin.token())).isEqualTo(200);

        TenantUsageResponse usage = get("/api/v1/tenant/usage", memberLogin.token(),
                new ParameterizedTypeReference<ApiResponse<TenantUsageResponse>>() {
                }).data();
        assertThat(usage.users()).isEqualTo(2);
        assertThat(usage.admins()).isEqualTo(2);
        assertThat(usage.tenantCode()).isEqualTo(code);
        assertThat(usage.estimatedMonthlyCents()).isEqualTo(200);

        // 平台侧也能查询用量
        ApiResponse<TenantUsageResponse> platformUsageResponse = get(
                "/api/v1/platform/tenants/" + tenant.tenantId() + "/usage", platformToken,
                new ParameterizedTypeReference<ApiResponse<TenantUsageResponse>>() {
                });
        TenantUsageResponse platformUsage = platformUsageResponse.data();
        assertThat(platformUsage.messages()).isGreaterThanOrEqualTo(0);
    }

    private String platformLogin() {
        return post("/api/v1/platform/auth/login",
                Map.of("username", "platform_admin", "password", "admin12345"), null,
                new ParameterizedTypeReference<ApiResponse<PlatformAuthResponse>>() {
                }).data().token();
    }

    private UserResponse register(String username, String tenantCode) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username, "tenantCode", tenantCode), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username, String tenantCode, String deviceId) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", deviceId, "platform", "LINUX", "deviceName", "test",
                        "tenantCode", tenantCode), null,
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

    private <T> ApiResponse<T> get(String path, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private int getStatus(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)),
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
