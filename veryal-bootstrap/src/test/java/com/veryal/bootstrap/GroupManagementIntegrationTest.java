package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.ErrorCode;
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
 * 群管理测试：管理员设置、改名、踢人、退群、权限边界。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GroupManagementIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void manageGroupMembersAndRoles() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("gm_owner_" + suffix);
        UserResponse admin = register("gm_admin_" + suffix);
        UserResponse member = register("gm_member_" + suffix);
        UserResponse leaver = register("gm_leaver_" + suffix);

        AuthResponse ownerLogin = login("gm_owner_" + suffix, "gmo-" + suffix);
        AuthResponse adminLogin = login("gm_admin_" + suffix, "gma-" + suffix);
        AuthResponse memberLogin = login("gm_member_" + suffix, "gmm-" + suffix);
        AuthResponse leaverLogin = login("gm_leaver_" + suffix, "gml-" + suffix);

        GroupResponse group = post("/api/v1/groups",
                Map.of("name", "management", "memberIds",
                        List.of(admin.userId(), member.userId(), leaver.userId())),
                ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
        String groupId = group.groupId();

        // 群主设置管理员
        GroupResponse promoted = put("/api/v1/groups/" + groupId + "/members/" + admin.userId() + "/role",
                Map.of("role", "ADMIN"), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
        assertThat(promoted.memberIds()).contains(admin.userId());

        // 管理员可以改名
        ApiResponse<GroupResponse> renamed = put("/api/v1/groups/" + groupId,
                Map.of("name", "renamed-group"), adminLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(renamed.data().name()).isEqualTo("renamed-group");

        // 管理员踢普通成员
        GroupResponse kicked = delete("/api/v1/groups/" + groupId + "/members/" + member.userId(),
                adminLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
        assertThat(kicked.memberIds()).doesNotContain(member.userId());

        // 被踢成员不能再发消息
        ResponseEntity<ApiResponse<Void>> forbiddenSend = rest.exchange(
                "/api/v1/conversations/" + groupId + "/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("conversationId", groupId, "clientMessageId", "x",
                        "type", "TEXT", "content", "hi"), headers(memberLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbiddenSend.getStatusCode().value()).isEqualTo(403);

        // 成员退群后不可见
        ApiResponse<Void> left = post("/api/v1/groups/" + groupId + "/leave", null,
                leaverLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(left.successful()).isTrue();
        ResponseEntity<ApiResponse<GroupResponse>> gone = rest.exchange("/api/v1/groups/" + groupId,
                HttpMethod.GET, new HttpEntity<>(headers(leaverLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(gone.getStatusCode().value()).isEqualTo(403);

        // 群主不能直接退群
        ResponseEntity<ApiResponse<Void>> ownerLeave = rest.exchange(
                "/api/v1/groups/" + groupId + "/leave", HttpMethod.POST,
                new HttpEntity<>(headers(ownerLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(ownerLeave.getStatusCode().value()).isEqualTo(400);
        assertThat(ownerLeave.getBody()).isNotNull();
        assertThat(ownerLeave.getBody().code()).isEqualTo(ErrorCode.INVALID_ARGUMENT.code());

        // 管理员不能改角色，也不能踢群主
        ResponseEntity<ApiResponse<GroupResponse>> roleByAdmin = rest.exchange(
                "/api/v1/groups/" + groupId + "/members/" + leaver.userId() + "/role", HttpMethod.PUT,
                new HttpEntity<>(Map.of("role", "MEMBER"), headers(adminLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(roleByAdmin.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<ApiResponse<GroupResponse>> kickOwner = rest.exchange(
                "/api/v1/groups/" + groupId + "/members/" + owner.userId(), HttpMethod.DELETE,
                new HttpEntity<>(headers(adminLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(kickOwner.getStatusCode().value()).isEqualTo(403);

        // 群主恢复成员角色
        GroupResponse demoted = put("/api/v1/groups/" + groupId + "/members/" + admin.userId() + "/role",
                Map.of("role", "MEMBER"), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
        assertThat(demoted.ownerId()).isEqualTo(owner.userId());
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

    private <T> ApiResponse<T> put(String path, Object body, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.PUT,
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
