package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.PushDeviceResponse;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推送设备 token 注册/查询/注销。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PushDeviceIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerListAndUnregisterDevice() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse user = register("push_" + suffix);
        AuthResponse login = login("push_" + suffix);

        PushDeviceResponse device = post("/api/v1/push/devices",
                Map.of("deviceId", login.deviceId(), "platform", "ANDROID",
                        "token", "fcm-token-" + suffix), login.token(),
                new ParameterizedTypeReference<ApiResponse<PushDeviceResponse>>() {
                }).data();
        assertThat(device.token()).isEqualTo("fcm-token-" + suffix);
        assertThat(device.platform()).isEqualTo("ANDROID");

        ApiResponse<List<PushDeviceResponse>> devices = get("/api/v1/push/devices",
                login.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(devices.data()).extracting(PushDeviceResponse::deviceId)
                .contains(login.deviceId());

        rest.exchange("/api/v1/push/devices/" + login.deviceId(), HttpMethod.DELETE,
                new HttpEntity<>(headers(login.token())), new ParameterizedTypeReference<>() {
                });
        ApiResponse<List<PushDeviceResponse>> after = get("/api/v1/push/devices",
                login.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(after.data()).allMatch(item -> !item.enabled());
        assertThat(user.userId()).isPositive();
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", "push-device-" + username, "platform", "ANDROID",
                        "deviceName", "test"), null,
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
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
        headers.setBearerAuth(token);
        return headers;
    }
}
