package com.veryal.bootstrap;

import com.veryal.access.http.dto.MfaSetupResponse;
import com.veryal.access.http.dto.PlatformAdminResponse;
import com.veryal.access.http.dto.PlatformAuthResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.security.Totp;
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

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台管理员 TOTP MFA：setup → enable → 登录校验 → disable。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlatformMfaIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void mfaSetupEnableLoginAndDisable() {
        String suffix = Long.toString(System.nanoTime());
        String adminToken = login("platform_admin", "admin12345", null).token();
        String name = "mfa_" + suffix.substring(Math.max(0, suffix.length() - 8));

        post("/api/v1/platform/admins", Map.of("username", name, "password", "mfapass123",
                "role", "SUPPORT"), adminToken,
                new ParameterizedTypeReference<ApiResponse<PlatformAdminResponse>>() {
                });
        String supportToken = login(name, "mfapass123", null).token();

        MfaSetupResponse setup = post("/api/v1/platform/auth/mfa/setup", Map.of(), supportToken,
                new ParameterizedTypeReference<ApiResponse<MfaSetupResponse>>() {
                }).data();
        assertThat(setup.secret()).isNotBlank();
        assertThat(setup.otpauthUrl()).startsWith("otpauth://totp/");

        String code = Totp.code(setup.secret(), Instant.now().getEpochSecond());
        post("/api/v1/platform/auth/mfa/enable", Map.of("code", code), supportToken,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        ResponseEntity<ApiResponse<PlatformAuthResponse>> missing = rest.exchange(
                "/api/v1/platform/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", name, "password", "mfapass123"), jsonHeaders()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(missing.getStatusCode().value()).isEqualTo(401);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().code()).isEqualTo(40105);

        PlatformAuthResponse mfaLogin = login(name, "mfapass123",
                Totp.code(setup.secret(), Instant.now().getEpochSecond()));
        assertThat(mfaLogin.token()).isNotBlank();

        post("/api/v1/platform/auth/mfa/disable", Map.of("code",
                Totp.code(setup.secret(), Instant.now().getEpochSecond())), supportToken,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(login(name, "mfapass123", null).token()).isNotBlank();
    }

    private PlatformAuthResponse login(String username, String password, String mfaCode) {
        ResponseEntity<ApiResponse<PlatformAuthResponse>> response = rest.exchange(
                "/api/v1/platform/auth/login", HttpMethod.POST,
                new HttpEntity<>(mfaCode == null
                        ? Map.of("username", username, "password", password)
                        : Map.of("username", username, "password", password, "mfaCode", mfaCode),
                        jsonHeaders()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        HttpHeaders headers = jsonHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
