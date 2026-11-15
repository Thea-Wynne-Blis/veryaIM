package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupModerationResponse;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.application.message.SendMessageCommand;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.message.MessageType;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 群公告、全员禁言、成员禁言与 @ 校验。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GroupModerationIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MessageApplicationService messageApplicationService;

    @Test
    void announcementAndMuteControlMessageSending() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("gm_owner_" + suffix);
        UserResponse member = register("gm_member_" + suffix);
        AuthResponse ownerLogin = login("gm_owner_" + suffix);
        AuthResponse memberLogin = login("gm_member_" + suffix);
        GroupResponse group = post("/api/v1/groups",
                Map.of("name", "Moderation " + suffix, "memberIds", java.util.List.of(member.userId())),
                ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();

        GroupModerationResponse updated = put("/api/v1/groups/" + group.groupId() + "/announcement",
                Map.of("announcement", "Welcome"), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                }).data();
        assertThat(updated.announcement()).isEqualTo("Welcome");

        ApiResponse<GroupModerationResponse> memberViewResponse = get(
                "/api/v1/groups/" + group.groupId() + "/moderation", memberLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                });
        GroupModerationResponse memberView = memberViewResponse.data();
        assertThat(memberView.announcement()).isEqualTo("Welcome");

        post("/api/v1/groups/" + group.groupId() + "/mute", Map.of("muteAll", true),
                ownerLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                });
        assertThatThrownBy(() -> send(group.groupId(), member.userId(), "gm-c1-" + suffix,
                "{\"text\":\"hi\"}"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GROUP_MUTED));
        assertThat(send(group.groupId(), owner.userId(), "gm-c2-" + suffix,
                "{\"text\":\"owner\"}")).isNotNull();

        post("/api/v1/groups/" + group.groupId() + "/mute", Map.of("muteAll", false),
                ownerLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                });
        post("/api/v1/groups/" + group.groupId() + "/members/" + member.userId() + "/mute",
                Map.of("minutes", 60), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                });
        assertThatThrownBy(() -> send(group.groupId(), member.userId(), "gm-c3-" + suffix,
                "{\"text\":\"hi\"}"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GROUP_MUTED));

        // @ 非群成员被拒绝
        assertThatThrownBy(() -> send(group.groupId(), owner.userId(), "gm-c4-" + suffix,
                "{\"text\":\"@x\",\"mentions\":[999999999]}"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));

        // 解除成员禁言后可发送，@ 群成员成功
        post("/api/v1/groups/" + group.groupId() + "/members/" + member.userId() + "/mute",
                Map.of("minutes", 0), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                });
        assertThat(send(group.groupId(), member.userId(), "gm-c5-" + suffix,
                "{\"text\":\"@owner\",\"mentions\":[" + owner.userId() + "]}")).isNotNull();
    }

    private SendMessageCommand sendCommand(String groupId, long senderId, String clientId,
                                           String content) {
        return new SendMessageCommand(1L, senderId, "gm-device", groupId, clientId,
                MessageType.TEXT, content.getBytes(StandardCharsets.UTF_8));
    }

    private Object send(String groupId, long senderId, String clientId, String content) {
        return messageApplicationService.send(sendCommand(groupId, senderId, clientId, content));
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", "gm-" + username, "platform", "LINUX", "deviceName", "test"), null,
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                }).data();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        return exchange(path, HttpMethod.POST, body, token, type);
    }

    private <T> ApiResponse<T> put(String path, Object body, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        return exchange(path, HttpMethod.PUT, body, token, type);
    }

    private <T> ApiResponse<T> get(String path, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        return exchange(path, HttpMethod.GET, null, token, type);
    }

    private <T> ApiResponse<T> exchange(String path, HttpMethod method, Object body, String token,
                                        ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, method,
                new HttpEntity<>(body, headers(token)), type);
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
