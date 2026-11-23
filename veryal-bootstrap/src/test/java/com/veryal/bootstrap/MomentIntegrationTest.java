package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.MomentResponse;
import com.veryal.access.http.dto.MomentSettingsResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.core.CursorPage;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 朋友圈：发布、信息流分页、好友边界与「可见天数」过滤。
 *
 * <p>这里同时兜住两类容易回归的问题：H2 schema 漏建朋友圈表、以及
 * 信息流里用了 H2 不支持的 MySQL 日期函数。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MomentIntegrationTest {

    private static final AtomicLong FRIEND_IDS = new AtomicLong(System.nanoTime());

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publishesAndReadsOwnMoment() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("mo_alice_" + suffix);
        AuthResponse aliceLogin = login("mo_alice_" + suffix, "mo-a-" + suffix);

        String content = "第一条朋友圈 " + suffix;
        MomentResponse published = publish(content, aliceLogin.token());
        assertThat(published.content()).isEqualTo(content);
        assertThat(published.visibility()).isEqualTo("ALL_FRIENDS");
        assertThat(published.mine()).isTrue();

        CursorPage<MomentResponse> feed = feed("/api/v1/moments/feed", aliceLogin.token());
        assertThat(feed.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(published.id());
                    assertThat(item.content()).isEqualTo(content);
                    assertThat(item.author().userId()).isEqualTo(alice.userId());
                });

        CursorPage<MomentResponse> mine = feed("/api/v1/moments/mine", aliceLogin.token());
        assertThat(mine.items()).hasSize(1);

        MomentSettingsResponse settings = get("/api/v1/moments/settings", aliceLogin.token(),
                new ParameterizedTypeReference<ApiResponse<MomentSettingsResponse>>() {
                }).data();
        assertThat(settings.visibleDays()).isZero();
    }

    @Test
    void friendFeedHonorsAuthorVisibleDays() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("mo2_alice_" + suffix);
        UserResponse bob = register("mo2_bob_" + suffix);
        AuthResponse aliceLogin = login("mo2_alice_" + suffix, "mo2-a-" + suffix);
        AuthResponse bobLogin = login("mo2_bob_" + suffix, "mo2-b-" + suffix);
        befriend(bob.userId(), alice.userId());

        MomentResponse old = publish("十天前 " + suffix, aliceLogin.token());
        MomentResponse recent = publish("刚刚 " + suffix, aliceLogin.token());
        backdate(old.id(), 10);

        // 默认 0 = 全部可见。
        assertThat(ids(feed("/api/v1/moments/feed", bobLogin.token())))
                .containsExactly(recent.id(), old.id());

        // 作者把可见范围收窄到 3 天：好友只看得到新动态。
        updateVisibleDays(aliceLogin.token(), 3);
        assertThat(ids(feed("/api/v1/moments/feed", bobLogin.token())))
                .containsExactly(recent.id());

        // 作者本人不受可见天数影响。
        assertThat(ids(feed("/api/v1/moments/mine", aliceLogin.token())))
                .containsExactly(recent.id(), old.id());

        // 改回「全部」后旧动态重新可见。
        updateVisibleDays(aliceLogin.token(), 0);
        assertThat(ids(feed("/api/v1/moments/feed", bobLogin.token())))
                .containsExactly(recent.id(), old.id());

        // 非好友访问作者主页是 403。
        UserResponse outsider = register("mo2_out_" + suffix);
        AuthResponse outsiderLogin = login("mo2_out_" + suffix, "mo2-c-" + suffix);
        ResponseEntity<ApiResponse<CursorPage<MomentResponse>>> forbidden = rest.exchange(
                "/api/v1/moments/users/" + alice.userId(), HttpMethod.GET,
                new HttpEntity<>(headers(outsiderLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
    }

    private MomentResponse publish(String content, String token) {
        return post("/api/v1/moments", Map.of(
                        "content", content,
                        "visibility", "ALL_FRIENDS",
                        "audienceUserIds", List.of(),
                        "media", List.of()),
                token, new ParameterizedTypeReference<ApiResponse<MomentResponse>>() {
                }).data();
    }

    private void updateVisibleDays(String token, int visibleDays) {
        ResponseEntity<ApiResponse<MomentSettingsResponse>> response = rest.exchange(
                "/api/v1/moments/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of("visibleDays", visibleDays), headers(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().visibleDays()).isEqualTo(visibleDays);
    }

    /** 直接写好友关系，避免测试把「加好友申请 → 同意」的流程再跑一遍。 */
    private void befriend(long userId, long friendId) {
        jdbc.update("INSERT INTO im_friend (id, user_id, friend_id, remark, created_at)"
                        + " VALUES (?, ?, ?, NULL, ?)",
                FRIEND_IDS.incrementAndGet(), userId, friendId, Timestamp.from(Instant.now()));
    }

    /** 把动态创建时间往前拨，验证 author.visible_days 的过滤边界。 */
    private void backdate(long momentId, int days) {
        jdbc.update("UPDATE im_moment SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS)), momentId);
    }

    private List<Long> ids(CursorPage<MomentResponse> page) {
        return page.items().stream().map(MomentResponse::id).toList();
    }

    private CursorPage<MomentResponse> feed(String path, String token) {
        return get(path, token, new ParameterizedTypeReference<
                ApiResponse<CursorPage<MomentResponse>>>() {
        }).data();
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register",
                Map.of("username", username, "password", "password123", "nickname", username),
                null, new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username, String deviceId) {
        return post("/api/v1/auth/login",
                Map.of("username", username, "password", "password123", "deviceId", deviceId,
                        "platform", "LINUX", "deviceName", "test"),
                null, new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
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

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
