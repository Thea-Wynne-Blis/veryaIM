package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.ConversationResponse;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers 矩阵：MySQL 8 + Redis 7 + RabbitMQ 3.13 启动完整应用。
 *
 * <p>验证内容：</p>
 * <ol>
 *     <li>Flyway 从 V1 到最新版本在真实 MySQL 上全部执行成功；</li>
 *     <li>注册 / 登录 / 建会话 / 发消息走通；</li>
 *     <li>消息写入后 Outbox 记录被 Publisher 发布为 status=1，证明 RabbitMQ 链路可用。</li>
 * </ol>
 *
 * <p>需要 Docker；无 Docker 时自动跳过。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TestcontainersMatrixIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.30"))
            .withDatabaseName("veryal")
            .withUsername("veryal")
            .withPassword("veryal123");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management"))
            .withAdminUser("veryal")
            .withAdminPassword("veryal123");

    private static volatile boolean rabbitReady;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        awaitTcp(RABBIT.getHost(), RABBIT.getMappedPort(5672), Duration.ofSeconds(90));
        awaitTcp("127.0.0.1", REDIS.getMappedPort(6379), Duration.ofSeconds(60));

        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.flyway.enabled", () -> true);

        registry.add("veryal.redis.enabled", () -> true);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.timeout", () -> "5s");

        registry.add("veryal.mq.enabled", () -> true);
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "veryal");
        registry.add("spring.rabbitmq.password", () -> "veryal123");
        registry.add("management.health.rabbit.enabled", () -> true);
        registry.add("veryal.node-id", () -> 110);
        registry.add("veryal.outbox.poll-interval-ms", () -> 200);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Test
    void fullStackStartsAndPublishesMessageThroughRabbit() {
        assertFlywayAtLatest();
        assertRedisReachable();

        String suffix = Long.toString(System.nanoTime());
        register("tc_alice_" + suffix);
        UserResponse bob = register("tc_bob_" + suffix);
        AuthResponse alice = login("tc_alice_" + suffix);
        login("tc_bob_" + suffix);

        ConversationResponse conversation = post("/api/v1/conversations",
                Map.of("peerUserId", bob.userId()), alice.token(),
                new ParameterizedTypeReference<ApiResponse<ConversationResponse>>() {
                }).data();
        String clientMessageId = "tc-" + suffix;
        ApiResponse<Void> sent = post("/api/v1/conversations/" + conversation.conversationId()
                        + "/messages",
                Map.of("conversationId", conversation.conversationId(),
                        "clientMessageId", clientMessageId, "type", "TEXT", "content", "hello"),
                alice.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(sent.successful()).isTrue();

        // 消息已落库
        Long messageId = jdbcTemplate.queryForObject(
                "SELECT id FROM im_message WHERE client_msg_id = ?", Long.class, clientMessageId);
        assertThat(messageId).isNotNull();

        // Outbox 被 Publisher 发布（status=1）证明 RabbitMQ 投递链路可用
        int published = awaitOutboxPublished(conversation.conversationId(), Duration.ofSeconds(30));
        assertThat(published).isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM im_message_delivery WHERE message_id = ?", Long.class,
                messageId)).isNotNull();
    }

    private void assertFlywayAtLatest() {
        String version = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = 1 "
                        + "ORDER BY installed_rank DESC LIMIT 1", String.class);
        assertThat(version).isEqualTo("22");
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(migrations).isEqualTo(22);
    }

    private void assertRedisReachable() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        RuntimeException last = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                String key = "tc:ping:" + System.nanoTime();
                redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(10));
                String value = redisTemplate.opsForValue().get(key);
                redisTemplate.delete(key);
                if ("1".equals(value)) {
                    return;
                }
            } catch (RuntimeException e) {
                last = e;
                sleep(500);
            }
        }
        throw new IllegalStateException("redis is not reachable from application context", last);
    }

    private int awaitOutboxPublished(String conversationId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM im_outbox WHERE status = 1 AND aggregate_id = ?",
                    Integer.class, conversationId);
            if (count != null && count > 0) {
                return count;
            }
            sleep(300);
        }
        return 0;
    }

    private static void awaitTcp(String host, int port, Duration timeout) {
        if (rabbitReady) {
            return;
        }
        Instant deadline = Instant.now().plus(timeout);
        RuntimeException last = null;
        while (Instant.now().isBefore(deadline)) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1000);
                rabbitReady = true;
                return;
            } catch (IOException e) {
                last = new IllegalStateException("rabbitmq not ready at " + host + ":" + port, e);
                sleep(500);
            }
        }
        throw last == null ? new IllegalStateException("rabbitmq not ready") : last;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", "tc-" + username, "platform", "LINUX", "deviceName", "test"),
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

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
