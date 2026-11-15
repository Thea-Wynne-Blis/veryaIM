package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuditLogResponse;
import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.domain.audit.AuditLog;
import com.veryal.domain.audit.AuditRepository;
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
 * 审计日志集成测试：登录与群管理敏感操作应落审计表。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AuditRepository auditRepository;

    @Test
    void loginAndGroupActionsAreAudited() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("au_owner_" + suffix);
        UserResponse member = register("au_member_" + suffix);
        AuthResponse ownerLogin = login("au_owner_" + suffix, "au-a-" + suffix);
        login("au_member_" + suffix, "au-b-" + suffix);

        GroupResponse group = post("/api/v1/groups",
                Map.of("name", "audit-group", "memberIds", List.of(member.userId())),
                ownerLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();

        delete("/api/v1/groups/" + group.groupId() + "/members/" + member.userId(),
                ownerLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                });

        List<AuditLog> logs = auditRepository.findByOperator(owner.userId(), 20);
        assertThat(logs).extracting(AuditLog::action)
                .contains("LOGIN", "GROUP_CREATE", "GROUP_MEMBER_REMOVE");
        assertThat(logs).allMatch(log -> log.operatorId() == owner.userId());

        ResponseEntity<ApiResponse<List<AuditLogResponse>>> endpoint = rest.exchange(
                "/api/v1/users/me/audit-logs?limit=20", HttpMethod.GET,
                new HttpEntity<>(headers(ownerLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(endpoint.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(endpoint.getBody()).isNotNull();
        assertThat(endpoint.getBody().data()).extracting(AuditLogResponse::action)
                .contains("LOGIN", "GROUP_CREATE", "GROUP_MEMBER_REMOVE");
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
