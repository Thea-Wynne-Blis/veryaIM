package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.FileResponse;
import com.veryal.access.http.dto.UploadSessionResponse;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分片上传：初始化、断点续传、缺少分片冲突、合并下载。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChunkedUploadIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void resumesAndCompletesChunkedUpload() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse user = register("chunk_user_" + suffix);
        AuthResponse login = login("chunk_user_" + suffix, "chunk-dev-" + suffix);
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);

        UploadSessionResponse session = init("chunk.txt", "text/plain", content.length, login.token());
        assertThat(session.totalChunks()).isEqualTo(3);
        assertThat(session.chunkSize()).isEqualTo(4);
        assertThat(session.uploadedChunks()).isEmpty();

        uploadChunk(session.uploadId(), 0, "0123".getBytes(StandardCharsets.UTF_8), login.token());
        uploadChunk(session.uploadId(), 1, "4567".getBytes(StandardCharsets.UTF_8), login.token());
        // 重复上传同一分片应幂等覆盖
        uploadChunk(session.uploadId(), 0, "0123".getBytes(StandardCharsets.UTF_8), login.token());

        UploadSessionResponse halfway = status(session.uploadId(), login.token());
        assertThat(halfway.uploadedChunks()).containsExactly(0, 1);

        ResponseEntity<ApiResponse<FileResponse>> incomplete = rest.exchange(
                "/api/v1/files/uploads/" + session.uploadId() + "/complete", HttpMethod.POST,
                new HttpEntity<>(headers(login.token())), new ParameterizedTypeReference<>() {
                });
        assertThat(incomplete.getStatusCode().value()).isEqualTo(409);

        uploadChunk(session.uploadId(), 2, "89".getBytes(StandardCharsets.UTF_8), login.token());
        ApiResponse<FileResponse> completed = post("/api/v1/files/uploads/" + session.uploadId()
                + "/complete", null, login.token(), new ParameterizedTypeReference<>() {
        });
        assertThat(completed.successful()).isTrue();
        assertThat(completed.data().sizeBytes()).isEqualTo(content.length);

        ResponseEntity<byte[]> download = rest.exchange(
                "/api/v1/files/" + completed.data().fileId() + "/content", HttpMethod.GET,
                new HttpEntity<>(headers(login.token())), byte[].class);
        assertThat(download.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(download.getBody()).isEqualTo(content);

        assertThat(status(session.uploadId(), login.token()).status()).isEqualTo("COMPLETED");
        assertThat(user.userId()).isPositive();
    }

    private UploadSessionResponse init(String filename, String contentType, long size, String token) {
        ApiResponse<UploadSessionResponse> response = post("/api/v1/files/uploads",
                Map.of("filename", filename, "contentType", contentType, "sizeBytes", size),
                token, new ParameterizedTypeReference<>() {
                });
        return response.data();
    }

    private void uploadChunk(long uploadId, int index, byte[] content, String token) {
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        ResponseEntity<ApiResponse<UploadSessionResponse>> response = rest.exchange(
                "/api/v1/files/uploads/" + uploadId + "/chunks/" + index, HttpMethod.PUT,
                new HttpEntity<>(content, headers), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private UploadSessionResponse status(long uploadId, String token) {
        ApiResponse<UploadSessionResponse> response = get("/api/v1/files/uploads/" + uploadId,
                token, new ParameterizedTypeReference<>() {
                });
        return response.data();
    }

    private UserResponse register(String username) {
        ApiResponse<UserResponse> response = post("/api/v1/auth/register",
                Map.of("username", username, "password", "password123", "nickname", username),
                null, new ParameterizedTypeReference<>() {
                });
        return response.data();
    }

    private AuthResponse login(String username, String deviceId) {
        ApiResponse<AuthResponse> response = post("/api/v1/auth/login",
                Map.of("username", username, "password", "password123", "deviceId", deviceId,
                        "platform", "LINUX", "deviceName", "test"),
                null, new ParameterizedTypeReference<>() {
                });
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
