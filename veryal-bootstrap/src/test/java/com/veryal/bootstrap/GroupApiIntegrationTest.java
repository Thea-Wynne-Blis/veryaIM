package com.veryal.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.GroupSummaryResponse;
import com.veryal.access.http.dto.MessagePageResponse;
import com.veryal.access.http.dto.SendMessageRequest;
import com.veryal.access.http.dto.SendMessageResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.ErrorCode;
import com.veryal.infrastructure.mysql.mapper.OutboxMapper;
import com.veryal.infrastructure.mysql.po.OutboxPO;
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
 * 群聊端到端测试：建群、成员权限、群消息、群事件类型。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GroupApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OutboxMapper outboxMapper;

    @Test
    void createGroupAndSendGroupMessage() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("g_owner_" + suffix);
        UserResponse member1 = register("g_m1_" + suffix);
        UserResponse member2 = register("g_m2_" + suffix);
        UserResponse outsider = register("g_out_" + suffix);

        AuthResponse ownerLogin = login("g_owner_" + suffix, "go-" + suffix);
        AuthResponse member1Login = login("g_m1_" + suffix, "gm1-" + suffix);
        AuthResponse outsiderLogin = login("g_out_" + suffix, "gout-" + suffix);

        GroupResponse group = post("/api/v1/groups",
                Map.of("name", "test-group", "memberIds", List.of(member1.userId(), member2.userId())),
                ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
        String groupId = group.groupId();
        assertThat(groupId).startsWith("g_");
        assertThat(group.memberIds()).containsExactlyInAnyOrder(owner.userId(), member1.userId(),
                member2.userId());

        SendMessageResponse first = send(groupId, "group-msg-1", "hello group", ownerLogin.token());
        assertThat(first.message().sequence()).isEqualTo(1);

        ApiResponse<MessagePageResponse> history = get(
                "/api/v1/conversations/" + groupId + "/messages?cursor=0&limit=10",
                member1Login.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(history.data().messages()).hasSize(1);
        assertThat(history.data().messages().get(0).content()).isEqualTo("hello group");

        // 非成员不可读、不可发
        ResponseEntity<ApiResponse<GroupResponse>> forbiddenGet = rest.exchange(
                "/api/v1/groups/" + groupId, HttpMethod.GET,
                new HttpEntity<>(headers(outsiderLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbiddenGet.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<ApiResponse<SendMessageResponse>> forbiddenSend = rest.exchange(
                "/api/v1/conversations/" + groupId + "/messages", HttpMethod.POST,
                new HttpEntity<>(new SendMessageRequest(groupId, "outsider-msg", "TEXT", "nope", null),
                        headers(outsiderLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbiddenSend.getStatusCode().value()).isEqualTo(403);
        assertThat(forbiddenSend.getBody()).isNotNull();

        // 群主添加成员后，新成员可以发送
        GroupResponse expanded = post("/api/v1/groups/" + groupId + "/members",
                Map.of("userIds", List.of(outsider.userId())), ownerLogin.token(),
                new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();
        assertThat(expanded.memberIds()).contains(outsider.userId());

        SendMessageResponse outsiderMessage = send(groupId, "outsider-msg-2", "now I can talk",
                outsiderLogin.token());
        assertThat(outsiderMessage.message().sequence()).isEqualTo(2);

        // 普通成员不能拉人
        ResponseEntity<ApiResponse<GroupResponse>> forbiddenAdd = rest.exchange(
                "/api/v1/groups/" + groupId + "/members", HttpMethod.POST,
                new HttpEntity<>(Map.of("userIds", List.of(owner.userId())),
                        headers(member1Login.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbiddenAdd.getStatusCode().value()).isEqualTo(403);

        // 群列表与事件类型
        ApiResponse<List<GroupSummaryResponse>> groups = get("/api/v1/groups", member1Login.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(groups.data()).anyMatch(item -> item.groupId().equals(groupId));

        Long groupEvents = outboxMapper.selectCount(Wrappers.<OutboxPO>lambdaQuery()
                .eq(OutboxPO::getAggregateId, groupId)
                .eq(OutboxPO::getEventType, "message.group.created"));
        assertThat(groupEvents).isEqualTo(2L);
        OutboxPO event = outboxMapper.selectOne(Wrappers.<OutboxPO>lambdaQuery()
                .eq(OutboxPO::getAggregateId, groupId)
                .orderByAsc(OutboxPO::getId)
                .last("LIMIT 1"));
        assertThat(event).isNotNull();
        assertThat(event.eventType).isEqualTo("message.group.created");
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

    private SendMessageResponse send(String conversationId, String clientMessageId, String content,
                                     String token) {
        ApiResponse<SendMessageResponse> response = post("/api/v1/conversations/" + conversationId
                        + "/messages",
                new SendMessageRequest(conversationId, clientMessageId, "TEXT", content, null), token,
                new ParameterizedTypeReference<>() {
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

    private <T> ApiResponse<T> get(String path, String token, ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.GET,
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
