package com.veryal.access.http.controller;

import com.veryal.application.avatar.AvatarApplicationService;
import com.veryal.application.file.FileApplicationService;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
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

import java.util.function.Supplier;

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
     * 头像会被频繁渲染，但换完也要尽快生效：短缓存 + ETag 304，
     * 列表页重复打开时基本都是零字节的 304。
     */
    private static final String PUBLIC_CACHE = "public, max-age=60, stale-while-revalidate=600";
    /**
     * 「没设头像」也缓存：列表里几十个用户都没头像时，每次渲染都回源会把
     * 限流额度和数据库连接一起打满。60 秒足够短，用户换完头像很快能看到。
     */
    private static final String MISSING_CACHE = "public, max-age=60";

    private final AvatarApplicationService avatarApplicationService;

    @Operation(summary = "用户头像", description = "公开接口；未设置头像时返回 404。")
    @GetMapping("/user/{userId}")
    public ResponseEntity<InputStreamResource> user(@PathVariable long userId,
                                                    HttpServletRequest request) {
        return image(() -> avatarApplicationService.userAvatar(userId), request);
    }

    @Operation(summary = "群头像", description = "公开接口；未设置群头像时返回 404。")
    @GetMapping("/group/{groupId}")
    public ResponseEntity<InputStreamResource> group(@PathVariable String groupId,
                                                     HttpServletRequest request) {
        return image(() -> avatarApplicationService.groupAvatar(groupId), request);
    }

    private ResponseEntity<InputStreamResource> image(
            Supplier<FileApplicationService.FileStream> loader, HttpServletRequest request) {
        FileApplicationService.FileStream content;
        try {
            content = loader.get();
        } catch (BusinessException e) {
            if (e.errorCode() == ErrorCode.NOT_FOUND) {
                return notSet();
            }
            throw e;
        }
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

    /**
     * 「没有头像」不是错误，而是一个可以短暂缓存的状态：
     * 这样未设置头像的用户不会在每次列表渲染时都打一次数据库。
     */
    private static ResponseEntity<InputStreamResource> notSet() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, MISSING_CACHE);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentLength(0);
        return new ResponseEntity<>(headers, HttpStatus.NOT_FOUND);
    }
}
