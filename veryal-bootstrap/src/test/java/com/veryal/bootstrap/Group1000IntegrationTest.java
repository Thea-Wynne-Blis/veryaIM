package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.application.group.GroupApplicationService;
import com.veryal.application.group.GroupView;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.application.message.SendMessageCommand;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.id.IdGenerator;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1000 人群容量验证：直插 999 个成员后建群并发送一条消息。
 * 该测试验证上限与扇出准备，不等价于并发压测。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Group1000IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private GroupApplicationService groupApplicationService;

    @Autowired
    private MessageApplicationService messageApplicationService;

    @Test
    void createThousandMemberGroupAndSendMessage() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("g1000_owner_" + suffix);
        AuthResponse ownerLogin = login("g1000_owner_" + suffix);
        String hash = "$pbkdf2-sha256$120000$loadsalt$loadhash";
        Timestamp now = Timestamp.from(Instant.now());
        List<Object[]> rows = new ArrayList<>();
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 999; i++) {
            long id = idGenerator.nextId();
            memberIds.add(id);
            rows.add(new Object[]{id, 1L, "g1000_m" + i + "_" + suffix, "m" + i, null, 1, hash,
                    now, now});
        }
        long start = System.nanoTime();
        jdbcTemplate.batchUpdate("INSERT INTO im_user "
                + "(id, tenant_id, username, nickname, avatar, status, password_hash, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);

        GroupView group = groupApplicationService.create(1L, owner.userId(), "1000 Group",
                memberIds);
        assertThat(group.memberIds()).hasSize(1000);
        messageApplicationService.send(new SendMessageCommand(1L, owner.userId(), "load-device",
                group.groupId(), "load-msg-" + suffix, MessageType.TEXT,
                "hello 1000".getBytes(StandardCharsets.UTF_8)));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("GROUP_1000_ELAPSED_MS=" + elapsedMs);
        assertThat(elapsedMs).isLessThan(30_000);

        // 100 个不同成员并发发送，验证 1000 人群下的并发写入/序号分配。
        int concurrentSends = 100;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        long concurrentStart = System.nanoTime();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < concurrentSends; i++) {
                long senderId = memberIds.get(i);
                int index = i;
                futures.add(pool.submit(() -> messageApplicationService.send(
                        new SendMessageCommand(1L, senderId, "load-device", group.groupId(),
                                "load-concurrent-" + suffix + "-" + index, MessageType.TEXT,
                                ("concurrent " + index).getBytes(StandardCharsets.UTF_8)))));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new AssertionError("concurrent group send failed", e);
        } finally {
            pool.shutdownNow();
        }
        long concurrentMs = (System.nanoTime() - concurrentStart) / 1_000_000;
        System.out.println("GROUP_1000_CONCURRENT_MS=" + concurrentMs
                + " THROUGHPUT=" + (concurrentSends * 1000L / Math.max(1, concurrentMs)) + "/s");
        assertThat(concurrentMs).isLessThan(30_000);
    }

    private UserResponse register(String username) {
        ResponseEntity<ApiResponse<UserResponse>> response = rest.exchange("/api/v1/auth/register",
                HttpMethod.POST, new HttpEntity<>(Map.of("username", username,
                        "password", "password123", "nickname", username), headers()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private AuthResponse login(String username) {
        ResponseEntity<ApiResponse<AuthResponse>> response = rest.exchange("/api/v1/auth/login",
                HttpMethod.POST, new HttpEntity<>(Map.of("username", username,
                        "password", "password123", "deviceId", "g1000-" + username,
                        "platform", "LINUX", "deviceName", "test"), headers()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
