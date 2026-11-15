package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.PlatformAuthResponse;
import com.veryal.access.http.dto.TenantExportResponse;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户数据导出与硬删除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TenantDataIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void exportAndDeleteTenantData() {
        String suffix = Long.toString(System.nanoTime());
        String platformToken = platformLogin();
        String code = "td" + suffix.substring(Math.max(0, suffix.length() - 8));
        TenantResponse tenant = post("/api/v1/platform/tenants",
                Map.of("code", code, "name", "Data Tenant", "maxUsers", 10,
                        "maxGroups", 5, "maxStorageBytes", 0), platformToken,
                new ParameterizedTypeReference<ApiResponse<TenantResponse>>() {
                }).data();
        UserResponse owner = register("td_owner_" + suffix, code);
        UserResponse member = register("td_member_" + suffix, code);
        AuthResponse ownerLogin = login("td_owner_" + suffix, code, "td-a-" + suffix);
        post("/api/v1/groups", Map.of("name", "Data Group",
                        "memberIds", List.of(member.userId())), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                });

        ApiResponse<TenantExportResponse> exportedResponse = get(
                "/api/v1/platform/tenants/" + tenant.tenantId() + "/export", platformToken,
                new ParameterizedTypeReference<ApiResponse<TenantExportResponse>>() {
                });
        TenantExportResponse exported = exportedResponse.data();
        assertThat(exported.tenantCode()).isEqualTo(code);
        assertThat(exported.userList()).hasSize(2);
        assertThat(exported.groupList()).hasSize(1);
        assertThat(exported.users()).isEqualTo(2);

        // 确认码错误时拒绝删除
        assertThat(deleteStatus(tenant.tenantId(), "wrong-" + code, platformToken)).isEqualTo(400);

        assertThat(deleteStatus(tenant.tenantId(), code, platformToken)).isEqualTo(200);
        assertThat(loginStatus("td_owner_" + suffix, code)).isEqualTo(404);
        assertThat(getStatus("/api/v1/platform/tenants/" + tenant.tenantId() + "/usage",
                platformToken)).isEqualTo(404);
    }

    private int deleteStatus(long tenantId, String confirmCode, String token) {
        return rest.exchange("/api/v1/platform/tenants/" + tenantId, HttpMethod.DELETE,
                new HttpEntity<>(Map.of("confirmCode", confirmCode), headers(token)),
                new ParameterizedTypeReference<>() {
                }).getStatusCode().value();
    }

    private int loginStatus(String username, String tenantCode) {
        return rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", "password123",
                        "deviceId", "td-login", "platform", "LINUX", "deviceName", "test",
                        "tenantCode", tenantCode), headers(null)),
                new ParameterizedTypeReference<>() {
                }).getStatusCode().value();
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
