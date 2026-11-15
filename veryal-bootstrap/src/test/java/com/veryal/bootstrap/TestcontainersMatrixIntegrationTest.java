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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers 矩阵：MySQL 8 + Redis 7 + RabbitMQ 3.13 启动完整应用并跑通消息链路。
 * 需要 Docker；本机无 Docker 时该测试会被跳过。
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
            DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        System.out.println("TC_RABBIT=" + RABBIT.getHost() + ":" + RABBIT.getMappedPort(5672)
                + " TC_REDIS=" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379)
                + " TC_MYSQL=" + MYSQL.getJdbcUrl());
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.flyway.enabled", () -> true);

        registry.add("veryal.redis.enabled", () -> true);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("veryal.mq.enabled", () -> true);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("management.health.rabbit.enabled", () -> true);
        registry.add("veryal.node-id", () -> 110);
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void fullStackStartsAndDeliversMessage() {
        String suffix = Long.toString(System.nanoTime());
        register("tc_alice_" + suffix);
        UserResponse bob = register("tc_bob_" + suffix);
        AuthResponse alice = login("tc_alice_" + suffix);
        login("tc_bob_" + suffix);

        ConversationResponse conversation = post("/api/v1/conversations",
                Map.of("peerUserId", bob.userId()), alice.token(),
                new ParameterizedTypeReference<ApiResponse<ConversationResponse>>() {
                }).data();
        ApiResponse<Void> sent = post("/api/v1/conversations/" + conversation.conversationId()
                        + "/messages",
                Map.of("conversationId", conversation.conversationId(),
                        "clientMessageId", "tc-" + suffix, "type", "TEXT", "content", "hello"),
                alice.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(sent.successful()).isTrue();
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
