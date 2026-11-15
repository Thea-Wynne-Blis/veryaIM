package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.PlatformAdminResponse;
import com.veryal.access.http.dto.PlatformAuthResponse;
import com.veryal.access.http.dto.ReportResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.application.ops.OpsDashboardService;
import com.veryal.application.ops.OpsGroupService;
import com.veryal.application.ops.OpsReportService;
import com.veryal.application.ops.OpsUserService;
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
 * 运营后台：用户搜索/封禁、群搜索/解散、举报处理、概览、SUPPORT 只读。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpsBackendIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void opsCanModerateUsersGroupsAndReports() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("ops_owner_" + suffix);
        UserResponse reporter = register("ops_reporter_" + suffix);
        AuthResponse ownerLogin = login("ops_owner_" + suffix);
        AuthResponse reporterLogin = login("ops_reporter_" + suffix);
        GroupResponse group = post("/api/v1/groups",
                Map.of("name", "Ops Group " + suffix, "memberIds", List.of(reporter.userId())),
                ownerLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();

        ApiResponse<ReportResponse> report = post("/api/v1/reports",
                Map.of("targetType", "USER", "targetId", Long.toString(owner.userId()),
                        "reason", "abuse"), reporterLogin.token(),
                new ParameterizedTypeReference<ApiResponse<ReportResponse>>() {
                });
        assertThat(report.data().status()).isEqualTo("PENDING");

        String adminToken = platformLogin();
        String supportName = "ops_support_" + suffix.substring(Math.max(0, suffix.length() - 8));
        post("/api/v1/platform/admins", Map.of("username", supportName, "password", "support123",
                "role", "SUPPORT"), adminToken,
                new ParameterizedTypeReference<ApiResponse<PlatformAdminResponse>>() {
                });
        String supportToken = platformLogin(supportName, "support123").token();

        ApiResponse<List<OpsUserService.OpsUserView>> users = get(
                "/api/v1/platform/ops/users?keyword=ops_owner_" + suffix, supportToken,
                new ParameterizedTypeReference<ApiResponse<List<OpsUserService.OpsUserView>>>() {
                });
        assertThat(users.data()).extracting(OpsUserService.OpsUserView::userId)
                .contains(owner.userId());

        // SUPPORT 只读
        assertThat(postStatus("/api/v1/platform/ops/users/" + owner.userId() + "/ban",
                Map.of("reason", "test"), supportToken)).isEqualTo(403);

        assertThat(postStatus("/api/v1/platform/ops/users/" + owner.userId() + "/ban",
                Map.of("reason", "abuse"), adminToken)).isEqualTo(200);
        assertThat(loginStatus("ops_owner_" + suffix)).isEqualTo(403);
        assertThat(postStatus("/api/v1/platform/ops/users/" + owner.userId() + "/unban",
                Map.of("reason", "appeal"), adminToken)).isEqualTo(200);
        assertThat(loginStatus("ops_owner_" + suffix)).isEqualTo(200);

        OpsDashboardService.DashboardView dashboard = get(
                "/api/v1/platform/ops/dashboard", supportToken,
                new ParameterizedTypeReference<ApiResponse<OpsDashboardService.DashboardView>>() {
                }).data();
        assertThat(dashboard.users()).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.pendingReports()).isGreaterThanOrEqualTo(1);

        ApiResponse<List<OpsReportService.OpsReportView>> reports = get(
                "/api/v1/platform/ops/reports?status=PENDING", adminToken,
                new ParameterizedTypeReference<ApiResponse<List<OpsReportService.OpsReportView>>>() {
                });
        assertThat(reports.data()).extracting(OpsReportService.OpsReportView::reportId)
                .contains(report.data().reportId());

        OpsReportService.OpsReportView handled = post(
                "/api/v1/platform/ops/reports/" + report.data().reportId() + "/handle",
                Map.of("status", "HANDLED", "note", "confirmed"), adminToken,
                new ParameterizedTypeReference<ApiResponse<OpsReportService.OpsReportView>>() {
                }).data();
        assertThat(handled.status()).isEqualTo("HANDLED");
        assertThat(handled.handledBy()).isNotNull();

        ApiResponse<List<OpsGroupService.OpsGroupView>> groups = get(
                "/api/v1/platform/ops/groups?keyword=Ops Group " + suffix, supportToken,
                new ParameterizedTypeReference<ApiResponse<List<OpsGroupService.OpsGroupView>>>() {
                });
        assertThat(groups.data()).extracting(OpsGroupService.OpsGroupView::groupId)
                .contains(group.groupId());

        assertThat(postStatus("/api/v1/platform/ops/groups/" + group.groupId() + "/disband",
                Map.of("reason", "policy"), adminToken)).isEqualTo(200);
        assertThat(getStatus("/api/v1/groups/" + group.groupId(), reporterLogin.token()))
                .isEqualTo(404);
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", "ops-" + username, "platform", "LINUX", "deviceName", "test"), null,
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                }).data();
    }

    private int loginStatus(String username) {
        return rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", "password123",
                        "deviceId", "ops-check-" + username, "platform", "LINUX",
                        "deviceName", "test"), headers(null)),
                new ParameterizedTypeReference<>() {
                }).getStatusCode().value();
    }

    private String platformLogin() {
        return platformLogin("platform_admin", "admin12345").token();
    }

    private PlatformAuthResponse platformLogin(String username, String password) {
        return post("/api/v1/platform/auth/login", Map.of("username", username, "password", password),
                null, new ParameterizedTypeReference<ApiResponse<PlatformAuthResponse>>() {
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

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
