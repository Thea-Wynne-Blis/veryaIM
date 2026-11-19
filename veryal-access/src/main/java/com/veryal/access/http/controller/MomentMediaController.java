package com.veryal.access.http.controller;

import com.veryal.access.http.security.CurrentUser;
import com.veryal.access.http.support.FileResponses;
import com.veryal.application.file.FileApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.moment.MomentApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 朋友圈媒体：先校验动态可见性与媒体归属，再流式返回内容。
 *
 * <p>不复用 {@code /files/{id}/content} 的通用 ACL：朋友圈的可见性由动态、
 * 好友关系与可见名单共同决定，只有这里能做出正确判断。</p>
 */
@Tag(name = "朋友圈")
@RestController
@RequestMapping("/api/v1/moments/{momentId}/media")
@RequiredArgsConstructor
public class MomentMediaController {

    private final MomentApplicationService momentService;
    private final FileApplicationService fileService;

    @Operation(summary = "读取动态图片 / 视频",
            description = "支持 Range；`disposition=inline` 可就地预览。")
    @GetMapping("/{fileId}")
    public ResponseEntity<InputStreamResource> content(
            @CurrentUser AuthenticatedUser user, @PathVariable long momentId,
            @PathVariable long fileId,
            @RequestParam(value = "disposition", required = false) String disposition,
            HttpServletRequest request) {
        momentService.requireMedia(user.userId(), momentId, fileId);
        return FileResponses.stream(fileService.openGranted(fileId), disposition, request);
    }

    @Operation(summary = "读取动态图片缩略图", description = "等比缩放到 `w` 像素内并转 JPEG。")
    @GetMapping("/{fileId}/thumb")
    public ResponseEntity<byte[]> thumbnail(
            @CurrentUser AuthenticatedUser user, @PathVariable long momentId,
            @PathVariable long fileId, @RequestParam(defaultValue = "480") int w,
            HttpServletRequest request) {
        momentService.requireMedia(user.userId(), momentId, fileId);
        return FileResponses.image(fileService.thumbnailGranted(fileId, w), request);
    }
}
