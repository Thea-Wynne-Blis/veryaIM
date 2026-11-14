package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.FileResponse;
import com.veryal.access.http.dto.MessageResponse;
import com.veryal.access.http.dto.SendMessageResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.json.JsonUtils;
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
 * 多消息类型集成测试：文本/图片/表情/语音策略与文件归属校验。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MessageTypeIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void validatesFileBackedAndTextTypes() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("mt_alice_" + suffix);
        UserResponse bob = register("mt_bob_" + suffix);
        AuthResponse aliceLogin = login("mt_alice_" + suffix, "mt-a-" + suffix);
        AuthResponse bobLogin = login("mt_bob_" + suffix, "mt-b-" + suffix);

        String conversationId = createConversation(alice, bob, aliceLogin.token());

        long aliceFile = upload("image.png", "image/png", aliceLogin.token());
        long bobFile = upload("other.png", "image/png", bobLogin.token());

        SendMessageResponse image = send(conversationId, "mt-image-" + suffix, "IMAGE",
                JsonUtils.toJson(Map.of("fileId", aliceFile, "width", 100, "height", 200)),
                aliceLogin.token(), 200);
        assertThat(image.message().type()).isEqualTo("IMAGE");

        send(conversationId, "mt-image-missing-" + suffix, "IMAGE",
                JsonUtils.toJson(Map.of("width", 100)), aliceLogin.token(), 400);

        send(conversationId, "mt-image-foreign-" + suffix, "IMAGE",
                JsonUtils.toJson(Map.of("fileId", bobFile)), aliceLogin.token(), 403);

        SendMessageResponse emoji = send(conversationId, "mt-emoji-" + suffix, "EMOJI",
                "😀", aliceLogin.token(), 200);
        assertThat(emoji.message().type()).isEqualTo("EMOJI");

        SendMessageResponse voice = send(conversationId, "mt-voice-" + suffix, "AUDIO",
                JsonUtils.toJson(Map.of("fileId", aliceFile, "durationSeconds", 3)),
                aliceLogin.token(), 200);
        assertThat(voice.message().type()).isEqualTo("AUDIO");

        send(conversationId, "mt-video-" + suffix, "VIDEO",
                JsonUtils.toJson(Map.of("fileId", aliceFile, "durationSeconds", 5,
                        "width", 640, "height", 480)),
                aliceLogin.token(), 200);
    }

    private String createConversation(UserResponse alice, UserResponse bob, String token) {
        ApiResponse<com.veryal.access.http.dto.ConversationResponse> response = post(
                "/api/v1/conversations", Map.of("peerUserId", bob.userId()), token,
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.successful()).isTrue();
        return response.data().conversationId();
    }

    private SendMessageResponse send(String conversationId, String clientMessageId, String type,
                                     String content, String token, int expectedStatus) {
        HttpHeaders headers = headers(token);
        ResponseEntity<ApiResponse<SendMessageResponse>> response = rest.exchange(
                "/api/v1/conversations/" + conversationId + "/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("conversationId", conversationId,
                        "clientMessageId", clientMessageId, "type", type, "content", content), headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        if (expectedStatus >= 400) {
            assertThat(response.getBody()).isNotNull();
            return null;
        }
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private long upload(String filename, String contentType, String token) {
        byte[] content = ("binary-" + filename).getBytes(StandardCharsets.UTF_8);
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
        return response.getBody().data().fileId();
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
