package com.veryal.access.http.controller;

import com.veryal.application.avatar.AvatarApplicationService;
import com.veryal.application.file.FileApplicationService;
import com.veryal.domain.file.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 头像读取接口（公开，无需令牌）。
 *
 * <p>头像要在聊天列表、通讯录、朋友圈里直接作为 {@code <img src>} 使用，
 * 无法携带 Authorization 头，因此单独开一个只读出口。响应按 sha256 打 ETag，
 * 短缓存 5 分钟：列表页滚动时不必反复回源，换头像后也能很快生效。</p>
 *
 * <p>未设置头像返回 404，前端据此回退到文字头像。</p>
 */
@Tag(name = "头像")
@RestController
@RequestMapping("/api/v1/avatars")
@RequiredArgsConstructor
public class AvatarController {

    /**
     * 头像随时可能被换掉，但换完要立刻在所有端生效：让浏览器每次用 ETag 回源校验，
     * 未变化时只是一次 304，代价远小于「换了头像半小时还是旧图」。
     */
    private static final String PUBLIC_CACHE = "public, max-age=0, must-revalidate";

    private final AvatarApplicationService avatarApplicationService;

    @Operation(summary = "用户头像", description = "公开接口；未设置头像时返回 404。")
    @GetMapping("/user/{userId}")
    public ResponseEntity<InputStreamResource> user(@PathVariable long userId,
                                                    HttpServletRequest request) {
        return image(avatarApplicationService.userAvatar(userId), request);
    }

    @Operation(summary = "群头像", description = "公开接口；未设置群头像时返回 404。")
    @GetMapping("/group/{groupId}")
    public ResponseEntity<InputStreamResource> group(@PathVariable String groupId,
                                                     HttpServletRequest request) {
        return image(avatarApplicationService.groupAvatar(groupId), request);
    }

    private ResponseEntity<InputStreamResource> image(FileApplicationService.FileStream content,
                                                      HttpServletRequest request) {
        StoredFile file = content.file();
        String quoted = '"' + file.sha256() + '"';
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(quoted);
        headers.set(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE);
        headers.set("X-Content-Type-Options", "nosniff");

        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch != null
                && (ifNoneMatch.contains(quoted) || ifNoneMatch.contains(file.sha256()))) {
            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
        }

        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentLength(file.sizeBytes());
        return new ResponseEntity<>(new InputStreamResource(content.open(0, 0)), headers,
                HttpStatus.OK);
    }
}
