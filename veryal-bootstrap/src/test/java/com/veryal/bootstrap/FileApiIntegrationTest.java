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

    @Test
    void conversationMembersCanDownloadScopedFile() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse uploader = register("sc_file_user_" + suffix);
        UserResponse member = register("sc_file_member_" + suffix);
        UserResponse outsider = register("sc_file_outsider_" + suffix);
        AuthResponse uploaderLogin = login("sc_file_user_" + suffix, "sc-a-" + suffix);
        AuthResponse memberLogin = login("sc_file_member_" + suffix, "sc-b-" + suffix);
        AuthResponse outsiderLogin = login("sc_file_outsider_" + suffix, "sc-c-" + suffix);

        ApiResponse<com.veryal.access.http.dto.ConversationResponse> conversation = post(
                "/api/v1/conversations", Map.of("peerUserId", member.userId()), uploaderLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        String conversationId = conversation.data().conversationId();
        FileResponse uploaded = upload("scoped.txt".getBytes(StandardCharsets.UTF_8), "scoped.txt",
                uploaderLogin.token(), conversationId);

        ResponseEntity<byte[]> memberDownload = rest.exchange(
                "/api/v1/files/" + uploaded.fileId() + "/content", HttpMethod.GET,
                new HttpEntity<>(headers(memberLogin.token())), byte[].class);
        assertThat(memberDownload.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(memberDownload.getBody()).isEqualTo("scoped.txt".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<byte[]> outsiderDownload = rest.exchange(
                "/api/v1/files/" + uploaded.fileId() + "/content", HttpMethod.GET,
                new HttpEntity<>(headers(outsiderLogin.token())), byte[].class);
        assertThat(outsiderDownload.getStatusCode().value()).isEqualTo(403);

        delete("/api/v1/files/" + uploaded.fileId(), uploaderLogin.token());
        assertThat(outsider.userId()).isPositive();
    }

    @Test
    void supportsRangeRequestsAndInlineDisposition() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse user = register("range_user_" + suffix);
        AuthResponse login = login("range_user_" + suffix, "range-dev-" + suffix);
        byte[] content = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        FileResponse uploaded = upload(content, "clip.mp4", login.token());
        String url = "/api/v1/files/" + uploaded.fileId() + "/content";

        HttpHeaders headers = headers(login.token());
        headers.set(HttpHeaders.RANGE, "bytes=4-7");
        ResponseEntity<byte[]> partial = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
        assertThat(partial.getStatusCode().value()).isEqualTo(206);
        assertThat(partial.getBody()).isEqualTo("4567".getBytes(StandardCharsets.UTF_8));
        assertThat(partial.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 4-7/16");
        assertThat(partial.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");

        // 开区间：从 10 到结尾
        headers.set(HttpHeaders.RANGE, "bytes=10-");
        ResponseEntity<byte[]> tail = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
        assertThat(tail.getStatusCode().value()).isEqualTo(206);
        assertThat(tail.getBody()).isEqualTo("abcdef".getBytes(StandardCharsets.UTF_8));

        // 越界 → 416，并告知实际长度
        headers.set(HttpHeaders.RANGE, "bytes=99-200");
        ResponseEntity<byte[]> unsatisfiable = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
        assertThat(unsatisfiable.getStatusCode().value()).isEqualTo(416);
        assertThat(unsatisfiable.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes */16");

        // 语法不合法：忽略 Range，回整份内容
        headers.set(HttpHeaders.RANGE, "bytes=oops");
        ResponseEntity<byte[]> ignored = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
        assertThat(ignored.getStatusCode().value()).isEqualTo(200);
        assertThat(ignored.getBody()).isEqualTo(content);

        HttpHeaders inlineHeaders = headers(login.token());
        ResponseEntity<byte[]> inline = rest.exchange(url + "?disposition=inline", HttpMethod.GET,
                new HttpEntity<>(inlineHeaders), byte[].class);
        assertThat(inline.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline");

        delete("/api/v1/files/" + uploaded.fileId(), login.token());
        assertThat(user.userId()).isPositive();
    }

    @Test
    void rejectsSvgThumbnail() {
        String suffix = Long.toString(System.nanoTime());
        register("svg_user_" + suffix);
        AuthResponse login = login("svg_user_" + suffix, "svg-dev-" + suffix);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        FileResponse uploaded = upload(svg, "vector.svg", login.token());
        assertThat(uploaded.contentType()).isEqualTo("image/svg+xml");

        ResponseEntity<String> thumb = rest.exchange(
                "/api/v1/files/" + uploaded.fileId() + "/thumb", HttpMethod.GET,
                new HttpEntity<>(headers(login.token())), String.class);
        // SVG 是 XML 文档，直接回传会在本站 origin 执行脚本，必须拒绝
        assertThat(thumb.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<byte[]> content = rest.exchange(
                "/api/v1/files/" + uploaded.fileId() + "/content?disposition=inline",
                HttpMethod.GET, new HttpEntity<>(headers(login.token())), byte[].class);
        assertThat(content.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment");

        delete("/api/v1/files/" + uploaded.fileId(), login.token());
    }

    private FileResponse upload(byte[] content, String filename, String token) {
        return upload(content, filename, token, null);
    }

    private FileResponse upload(byte[] content, String filename, String token, String conversationId) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        if (conversationId != null) {
            body.add("conversationId", conversationId);
        }
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
