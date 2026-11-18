package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupMemberResponse;
import com.veryal.access.http.dto.GroupModerationResponse;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.UserResponse;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
        GroupResponse group = createGroup(ownerLogin, "Moderation " + suffix, List.of(member.userId()));

        GroupModerationResponse updated = put("/api/v1/groups/" + group.groupId() + "/announcement",
                Map.of("announcement", "Welcome"), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                }).data();
        assertThat(updated.announcement()).isEqualTo("Welcome");

        ApiResponse<GroupModerationResponse> memberViewResponse = get(
                "/api/v1/groups/" + group.groupId() + "/moderation", memberLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                });
        assertThat(memberViewResponse.data().announcement()).isEqualTo("Welcome");

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

        // 7 分钟档：到期时间在 6~8 分钟之间
        GroupModerationResponse muted = mute(group, member.userId(), "MUTE_7M", ownerLogin.token());
        assertThat(muted.mutedPermanently()).isFalse();
        assertThat(muted.mutedUntil()).isBetween(Instant.now().plus(Duration.ofMinutes(6)),
                Instant.now().plus(Duration.ofMinutes(8)));
        assertThatThrownBy(() -> send(group.groupId(), member.userId(), "gm-c3-" + suffix,
                "{\"text\":\"hi\"}"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GROUP_MUTED));

        // @ 非群成员被拒绝
        assertThatThrownBy(() -> send(group.groupId(), owner.userId(), "gm-c4-" + suffix,
                "{\"text\":\"@x\",\"mentions\":[999999999]}"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));

        // 解除禁言后可发送，@ 群成员成功
        mute(group, member.userId(), "UNMUTE", ownerLogin.token());
        assertThat(send(group.groupId(), member.userId(), "gm-c5-" + suffix,
                "{\"text\":\"@owner\",\"mentions\":[" + owner.userId() + "]}")).isNotNull();

        // 永久禁言：没有到期时间，用 mutedPermanently 表达
        GroupModerationResponse forever = mute(group, member.userId(), "MUTE_FOREVER",
                ownerLogin.token());
        assertThat(forever.mutedPermanently()).isTrue();
        assertThat(forever.mutedUntil()).isNull();
        assertThatThrownBy(() -> send(group.groupId(), member.userId(), "gm-c6-" + suffix,
                "{\"text\":\"hi\"}"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GROUP_MUTED));

        mute(group, member.userId(), "UNMUTE", ownerLogin.token());
        assertThat(send(group.groupId(), member.userId(), "gm-c7-" + suffix,
                "{\"text\":\"back\"}")).isNotNull();

        // 非法档位由请求校验拒绝
        assertThat(postStatus("/api/v1/groups/" + group.groupId() + "/members/"
                        + member.userId() + "/mute", Map.of("duration", "30"), ownerLogin.token()))
                .isEqualTo(400);
    }

    @Test
    void muteAndRoleChangesAreOwnerOnly() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("gr_owner_" + suffix);
        UserResponse admin = register("gr_admin_" + suffix);
        UserResponse member = register("gr_member_" + suffix);
        AuthResponse ownerLogin = login("gr_owner_" + suffix);
        AuthResponse adminLogin = login("gr_admin_" + suffix);
        AuthResponse memberLogin = login("gr_member_" + suffix);
        GroupResponse group = createGroup(ownerLogin, "Roles " + suffix,
                List.of(admin.userId(), member.userId()));
        String base = "/api/v1/groups/" + group.groupId();

        // 群主设置管理员
        put(base + "/members/" + admin.userId() + "/role", Map.of("role", "ADMIN"),
                ownerLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                });

        // 成员列表：角色与排序（群主在前，其次管理员）
        List<GroupMemberResponse> members = get(base + "/members", memberLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<GroupMemberResponse>>>() {
                }).data();
        assertThat(members).extracting(GroupMemberResponse::userId)
                .containsExactly(owner.userId(), admin.userId(), member.userId());
        assertThat(members.get(0).role()).isEqualTo("OWNER");
        assertThat(members.get(1).role()).isEqualTo("ADMIN");

        // 管理员不能禁言任何人，也不能开全员禁言
        assertThat(postStatus(base + "/members/" + member.userId() + "/mute",
                Map.of("duration", "MUTE_7M"), adminLogin.token())).isEqualTo(403);
        assertThat(postStatus(base + "/mute", Map.of("muteAll", true),
                adminLogin.token())).isEqualTo(403);
        // 普通成员也不能禁言
        assertThat(postStatus(base + "/members/" + admin.userId() + "/mute",
                Map.of("duration", "MUTE_7M"), memberLogin.token())).isEqualTo(403);
        // 管理员仍可改公告
        assertThat(putStatus(base + "/announcement", Map.of("announcement", "admin notice"),
                adminLogin.token())).isEqualTo(200);

        // 群主可以禁言管理员
        GroupModerationResponse mutedAdmin = mute(group, admin.userId(), "MUTE_30M",
                ownerLogin.token());
        assertThat(mutedAdmin.mutedUntil()).isNotNull();
        assertThatThrownBy(() -> send(group.groupId(), admin.userId(), "gr-c1-" + suffix,
                "{\"text\":\"hi\"}"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GROUP_MUTED));

        // 管理员不能禁言管理员
        assertThat(postStatus(base + "/members/" + member.userId() + "/mute",
                Map.of("duration", "MUTE_7M"), adminLogin.token())).isEqualTo(403);

        // 管理员只能踢普通成员，不能踢管理员；群主能踢管理员
        assertThat(deleteStatus(base + "/members/" + owner.userId(), adminLogin.token()))
                .isEqualTo(403);
        assertThat(deleteStatus(base + "/members/" + member.userId(), adminLogin.token()))
                .isEqualTo(200);

        // 成员列表里的禁言状态
        List<GroupMemberResponse> afterMute = get(base + "/members", ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<GroupMemberResponse>>>() {
                }).data();
        GroupMemberResponse adminRow = afterMute.stream()
                .filter(item -> item.userId() == admin.userId()).findFirst().orElseThrow();
        assertThat(adminRow.mutedUntil()).isNotNull();
        assertThat(adminRow.mutedPermanently()).isFalse();
    }

    private GroupResponse createGroup(AuthResponse ownerLogin, String name, List<Long> memberIds) {
        return post("/api/v1/groups", Map.of("name", name, "memberIds", memberIds),
                ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
    }

    private GroupModerationResponse mute(GroupResponse group, long userId, String duration,
                                         String token) {
        return post("/api/v1/groups/" + group.groupId() + "/members/" + userId + "/mute",
                Map.of("duration", duration), token,
                new ParameterizedTypeReference<ApiResponse<GroupModerationResponse>>() {
                }).data();
    }

    private SendMessageCommand sendCommand(String groupId, long senderId, String clientId,
                                           String content) {
        return new SendMessageCommand(senderId, "gm-device", groupId, clientId,
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

    private int postStatus(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                }).getStatusCode().value();
    }

    private int putStatus(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers(token)),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                }).getStatusCode().value();
    }

    private int deleteStatus(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                }).getStatusCode().value();
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
