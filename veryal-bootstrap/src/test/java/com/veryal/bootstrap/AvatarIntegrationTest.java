package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.GroupResponse;
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 头像接口测试：上传 / 公开读取 / 缓存 / 删除 / 群头像权限。
 *
 * <p>公开读取是头像能在 {@code <img src>} 里直接用的前提，因此这里特意不带令牌访问。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AvatarIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void userAvatarLifecycle() throws IOException {
        String suffix = Long.toString(System.nanoTime());
        UserResponse user = register("av_user_" + suffix);
        AuthResponse login = login("av_user_" + suffix, "avd-" + suffix);

        // 未设置头像时公开接口返回 404
        ResponseEntity<byte[]> absent = rest.getForEntity(
                "/api/v1/avatars/user/" + user.userId(), byte[].class);
        assertThat(absent.getStatusCode().value()).isEqualTo(404);

        byte[] png = pngBytes(96, 96);

        // 上传头像
        ResponseEntity<ApiResponse<UserResponse>> uploaded = rest.exchange(
                "/api/v1/users/me/avatar", HttpMethod.POST, multipart(png, login.token()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(uploaded.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(uploaded.getBody()).isNotNull();
        assertThat(uploaded.getBody().data().avatar()).isNotBlank();

        // 公开读取：不带 Authorization 也能拿到图片，且带 ETag 与公共缓存
        ResponseEntity<byte[]> served = rest.getForEntity(
                "/api/v1/avatars/user/" + user.userId(), byte[].class);
        assertThat(served.getStatusCode().value()).isEqualTo(200);
        assertThat(served.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.getType()).isEqualTo("image"));
        assertThat(served.getBody()).isEqualTo(png);
        assertThat(served.getHeaders().getETag()).isNotBlank();
        assertThat(served.getHeaders().getCacheControl()).contains("public");

        // If-None-Match 命中返回 304，列表滚动时不必重复下载
        HttpHeaders conditional = new HttpHeaders();
        conditional.setIfNoneMatch(served.getHeaders().getETag());
        ResponseEntity<byte[]> notModified = rest.exchange(
                "/api/v1/avatars/user/" + user.userId(), HttpMethod.GET,
                new HttpEntity<>(conditional), byte[].class);
        assertThat(notModified.getStatusCode().value()).isEqualTo(304);

        // 换头像后地址里的版本号变化，公开接口返回新图
        byte[] gif = gifBytes(64, 64);
        ResponseEntity<ApiResponse<UserResponse>> replaced = rest.exchange(
                "/api/v1/users/me/avatar", HttpMethod.POST,
                multipart(gif, login.token(), "image/gif", "avatar.gif"),
                new ParameterizedTypeReference<>() {
                });
        assertThat(replaced.getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<byte[]> updated = rest.getForEntity(
                "/api/v1/avatars/user/" + user.userId(), byte[].class);
        assertThat(updated.getBody()).isEqualTo(gif);

        // 删除头像后又回到 404
        ResponseEntity<ApiResponse<Void>> removed = rest.exchange(
                "/api/v1/users/me/avatar", HttpMethod.DELETE,
                new HttpEntity<>(headers(login.token())), new ParameterizedTypeReference<>() {
                });
        assertThat(removed.getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<byte[]> cleared = rest.getForEntity(
                "/api/v1/avatars/user/" + user.userId(), byte[].class);
        assertThat(cleared.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void groupAvatarRequiresAdmin() throws IOException {
        String suffix = Long.toString(System.nanoTime());
        UserResponse owner = register("av_owner_" + suffix);
        UserResponse member = register("av_member_" + suffix);
        UserResponse outsider = register("av_out_" + suffix);

        AuthResponse ownerLogin = login("av_owner_" + suffix, "avo-" + suffix);
        AuthResponse memberLogin = login("av_member_" + suffix, "avm-" + suffix);
        AuthResponse outsiderLogin = login("av_out_" + suffix, "avx-" + suffix);

        GroupResponse group = post("/api/v1/groups",
                Map.of("name", "avatar-group", "memberIds", List.of(member.userId())),
                ownerLogin.token(), new ParameterizedTypeReference<ApiResponse<GroupResponse>>() {
                }).data();

        // 未设置群头像时 404
        assertThat(rest.getForEntity("/api/v1/avatars/group/" + group.groupId(),
                byte[].class).getStatusCode().value()).isEqualTo(404);

        // 普通成员不能改群头像
        ResponseEntity<ApiResponse<GroupResponse>> byMember = rest.exchange(
                "/api/v1/groups/" + group.groupId() + "/avatar", HttpMethod.PUT,
                multipart(pngBytes(48, 48), memberLogin.token()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(byMember.getStatusCode().value()).isEqualTo(403);

        // 非成员也不能改
        ResponseEntity<ApiResponse<GroupResponse>> byOutsider = rest.exchange(
                "/api/v1/groups/" + group.groupId() + "/avatar", HttpMethod.PUT,
                multipart(pngBytes(48, 48), outsiderLogin.token()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(byOutsider.getStatusCode().value()).isEqualTo(403);

        // 群主上传成功，且响应里带上头像地址
        byte[] png = pngBytes(128, 128);
        ResponseEntity<ApiResponse<GroupResponse>> byOwner = rest.exchange(
                "/api/v1/groups/" + group.groupId() + "/avatar", HttpMethod.PUT,
                multipart(png, ownerLogin.token()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(byOwner.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(byOwner.getBody().data().avatar())
                .isEqualTo("/api/v1/avatars/group/" + group.groupId());

        // 任意已登录/未登录用户都能 <img src> 读取
        ResponseEntity<byte[]> served = rest.getForEntity(
                "/api/v1/avatars/group/" + group.groupId(), byte[].class);
        assertThat(served.getStatusCode().value()).isEqualTo(200);
        assertThat(served.getBody()).isEqualTo(png);

        // 群主删除群头像
        ResponseEntity<ApiResponse<GroupResponse>> cleared = rest.exchange(
                "/api/v1/groups/" + group.groupId() + "/avatar", HttpMethod.DELETE,
                new HttpEntity<>(headers(ownerLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(cleared.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rest.getForEntity("/api/v1/avatars/group/" + group.groupId(),
                byte[].class).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rejectsNonImageUpload() {
        String suffix = Long.toString(System.nanoTime());
        register("av_bad_" + suffix);
        AuthResponse login = login("av_bad_" + suffix, "avb-" + suffix);

        ResponseEntity<ApiResponse<Void>> response = rest.exchange(
                "/api/v1/users/me/avatar", HttpMethod.POST,
                multipart("not an image".getBytes(), login.token(), "image/png", "fake.png"),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private static byte[] pngBytes(int width, int height) throws IOException {
        return imageBytes("png", width, height);
    }

    private static byte[] gifBytes(int width, int height) throws IOException {
        return imageBytes("gif", width, height);
    }

    private static byte[] imageBytes(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(20, 120, 90));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private HttpEntity<MultiValueMap<String, Object>> multipart(byte[] content, String token) {
        return multipart(content, token, "image/png", "avatar.png");
    }

    /**
     * 组装 multipart 请求。
     *
     * <p>文件段显式带上 Content-Type：服务端按声明的类型做白名单校验，
     * 默认的 {@code application/octet-stream} 会被拒绝。</p>
     */
    private HttpEntity<MultiValueMap<String, Object>> multipart(byte[] content, String token,
                                                                String contentType, String name) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(
                contentType == null ? "application/octet-stream" : contentType));
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
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
