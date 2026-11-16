package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.DeviceResponse;
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
 * 登录设备列表：同平台同名记录合并、当前设备标记与移除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DeviceManagementIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void listGroupsAndRemoveDevices() {
        String suffix = Long.toString(System.nanoTime());
        String username = "dev_" + suffix;
        register(username);
        login(username, "dev-a-" + suffix, "Browser");
        login(username, "dev-b-" + suffix, "Browser");
        AuthResponse currentLogin = login(username, "dev-c-" + suffix, "Phone");

        ApiResponse<List<DeviceResponse>> before = get("/api/v1/users/me/devices",
                currentLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<DeviceResponse>>>() {
                });
        List<DeviceResponse> devices = before.data();
        // 两条 Browser 合并，Phone 是当前设备并排在最前。
        assertThat(devices).hasSize(2);
        assertThat(devices.get(0).deviceName()).isEqualTo("Phone");
        assertThat(devices.get(0).current()).isTrue();
        assertThat(devices.get(0).deviceCount()).isEqualTo(1);

        DeviceResponse browser = devices.stream()
                .filter(device -> "Browser".equals(device.deviceName()))
                .findFirst()
                .orElseThrow();
        assertThat(browser.deviceCount()).isEqualTo(2);
        assertThat(browser.current()).isFalse();

        // 移除 Browser：同平台同名的两条记录一起清理。
        ApiResponse<Integer> removed = delete("/api/v1/users/me/devices/" + browser.deviceId(),
                currentLogin.token(),
                new ParameterizedTypeReference<ApiResponse<Integer>>() {
                });
        assertThat(removed.data()).isEqualTo(2);

        ApiResponse<List<DeviceResponse>> after = get("/api/v1/users/me/devices",
                currentLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<DeviceResponse>>>() {
                });
        assertThat(after.data()).extracting(DeviceResponse::deviceName).containsExactly("Phone");

        // 当前设备不允许移除。
        assertThat(deleteStatus("/api/v1/users/me/devices/" + currentLogin.deviceId(),
                currentLogin.token())).isEqualTo(403);

        // 不能移除其他账号的设备。
        String otherName = "dev_other_" + suffix;
        register(otherName);
        AuthResponse other = login(otherName, "dev-other-" + suffix, "Browser");
        assertThat(deleteStatus("/api/v1/users/me/devices/" + other.deviceId(),
                currentLogin.token())).isEqualTo(404);
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username, String deviceId, String deviceName) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", deviceId, "platform", "WEB", "deviceName", deviceName), null,
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

    private <T> ApiResponse<T> delete(String path, String token,
                                      ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private int deleteStatus(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {
                }).getStatusCode().value();
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
