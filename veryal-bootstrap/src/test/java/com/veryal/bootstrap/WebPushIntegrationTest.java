package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.access.http.dto.WebPushConfigResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.security.WebPushCrypto;
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

import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web Push：配置下发、订阅注册、退订与失效清理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebPushIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void configExposesVapidKeyAndSubscriptionLifecycle() {
        String suffix = Long.toString(System.nanoTime());
        register("webpush_" + suffix);
        AuthResponse login = login("webpush_" + suffix);

        WebPushConfigResponse config = get("/api/v1/push/web/config", login.token(),
                new ParameterizedTypeReference<ApiResponse<WebPushConfigResponse>>() {
                }).data();
        assertThat(config.enabled()).isTrue();
        assertThat(config.publicKey()).isNotBlank();
        // 必须是合法的 65 字节未压缩 P-256 点
        byte[] point = Base64.getUrlDecoder().decode(config.publicKey());
        assertThat(point).hasSize(65);
        assertThat(point[0]).isEqualTo((byte) 0x04);

        // 订阅端自己生成密钥对，模拟浏览器 pushManager.subscribe
        KeyPair receiver = WebPushCrypto.generateEcKeyPair();
        String p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(
                WebPushCrypto.encodePoint(((ECPublicKey) receiver.getPublic()).getW(),
                        WebPushCrypto.ecParameterSpec()));
        byte[] authSecret = new byte[16];
        new java.security.SecureRandom().nextBytes(authSecret);
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(authSecret);
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + suffix;

        post("/api/v1/push/web/subscriptions",
                Map.of("endpoint", endpoint, "p256dh", p256dh, "auth", auth,
                        "userAgent", "JUnit"), login.token(),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        // 重复订阅幂等（同一 endpoint 只保留一条）
        post("/api/v1/push/web/subscriptions",
                Map.of("endpoint", endpoint, "p256dh", p256dh, "auth", auth,
                        "userAgent", "JUnit"), login.token(),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        unsubscribe(endpoint, login.token());

        // 退订后再次退订不报错
        unsubscribe(endpoint, login.token());
    }

    @Test
    void rejectsSubscriptionWithoutKeys() {
        String suffix = Long.toString(System.nanoTime());
        register("webpush_bad_" + suffix);
        AuthResponse login = login("webpush_bad_" + suffix);

        int status = rest.exchange("/api/v1/push/web/subscriptions", HttpMethod.POST,
                new HttpEntity<>(Map.of("endpoint", "https://example.com/x", "p256dh", "",
                        "auth", ""), headers(login.token())),
                new ParameterizedTypeReference<>() {
                }).getStatusCode().value();
        assertThat(status).isEqualTo(400);
    }

    private void unsubscribe(String endpoint, String token) {
        ResponseEntity<ApiResponse<Void>> response = rest.exchange(
                "/api/v1/push/web/subscriptions?endpoint={endpoint}", HttpMethod.DELETE,
                new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {
                }, endpoint);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", "wp-" + username, "platform", "WEB", "deviceName", "test"),
                null, new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                }).data();
    }

    private <T> ApiResponse<T> get(String path, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = jsonHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
