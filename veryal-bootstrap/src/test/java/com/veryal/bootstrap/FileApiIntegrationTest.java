package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.FileResponse;
import com.veryal.access.http.dto.FileUrlResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件 API 集成测试：本地存储策略下的上传、下载、权限、URL 与删除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FileApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void uploadDownloadUrlAndDelete() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse uploader = register("file_user_" + suffix);
        UserResponse other = register("file_other_" + suffix);
        AuthResponse uploaderLogin = login("file_user_" + suffix, "file-a-" + suffix);
        AuthResponse otherLogin = login("file_other_" + suffix, "file-b-" + suffix);

        byte[] content = "hello veryal file".getBytes(StandardCharsets.UTF_8);
        FileResponse uploaded = upload(content, "hello.txt", uploaderLogin.token());

        assertThat(uploaded.fileId()).isPositive();
        assertThat(uploaded.name()).isEqualTo("hello.txt");
        assertThat(uploaded.contentType()).isEqualTo("text/plain");
        assertThat(uploaded.sizeBytes()).isEqualTo(content.length);
        assertThat(uploaded.sha256()).hasSize(64);
        assertThat(uploaded.storageType()).isEqualTo("LOCAL");

        ResponseEntity<byte[]> downloaded = rest.exchange(
                "/api/v1/files/" + uploaded.fileId() + "/content", HttpMethod.GET,
                new HttpEntity<>(headers(uploaderLogin.token())), byte[].class);
        assertThat(downloaded.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(downloaded.getBody()).isEqualTo(content);
        assertThat(downloaded.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("hello.txt");

        ApiResponse<FileUrlResponse> url = get("/api/v1/files/" + uploaded.fileId() + "/url",
                uploaderLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(url.data().url()).isEqualTo("/api/v1/files/" + uploaded.fileId() + "/content");
        assertThat(url.data().expiresInSeconds()).isZero();

        ResponseEntity<byte[]> forbidden = rest.exchange(
                "/api/v1/files/" + uploaded.fileId() + "/content", HttpMethod.GET,
                new HttpEntity<>(headers(otherLogin.token())), byte[].class);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        delete("/api/v1/files/" + uploaded.fileId(), uploaderLogin.token());

        ResponseEntity<byte[]> gone = rest.exchange(
                "/api/v1/files/" + uploaded.fileId() + "/content", HttpMethod.GET,
                new HttpEntity<>(headers(uploaderLogin.token())), byte[].class);
        assertThat(gone.getStatusCode().value()).isEqualTo(404);
    }

    private FileResponse upload(byte[] content, String filename, String token) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<ApiResponse<FileResponse>> response = rest.exchange("/api/v1/files",
                HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
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

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    private <T> ApiResponse<T> get(String path, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    private void delete(String path, String token) {
        ResponseEntity<ApiResponse<Void>> response = rest.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
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
