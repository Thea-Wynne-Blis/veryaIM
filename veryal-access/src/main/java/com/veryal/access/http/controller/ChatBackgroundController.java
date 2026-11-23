package com.veryal.access.http.controller;

import com.veryal.access.http.dto.ChatBackgroundItem;
import com.veryal.access.http.dto.ChatBackgroundPresetRequest;
import com.veryal.access.http.dto.ChatBackgroundResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.access.http.support.MultipartFiles;
import com.veryal.application.chatbackground.ChatBackgroundApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 聊天背景接口。
 *
 * <p>所有设置都只属于当前用户：别人看不到、也读不到你上传的背景图。
 * 不传 {@code conversationId} 时操作的是全局背景；传了就只影响该会话。</p>
 */
@Tag(name = "聊天背景")
@RestController
@RequestMapping("/api/v1/chat-backgrounds")
@RequiredArgsConstructor
public class ChatBackgroundController {

    private final ChatBackgroundApplicationService chatBackgroundApplicationService;

    @Operation(summary = "我的聊天背景", description = "返回全局背景与各会话覆盖；会话级为空时客户端回退到全局。")
    @GetMapping
    public ApiResponse<ChatBackgroundResponse> view(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(ChatBackgroundResponse.from(
                chatBackgroundApplicationService.view(user.userId())));
    }

    @Operation(summary = "选择内置背景", description = "只传预设 ID；`conversationId` 为空时改全局。")
    @PutMapping("/preset")
    public ApiResponse<ChatBackgroundItem> setPreset(
            @CurrentUser AuthenticatedUser user,
            @RequestBody ChatBackgroundPresetRequest request) {
        return ApiResponse.ok(ChatBackgroundItem.from(
                chatBackgroundApplicationService.setPreset(user.userId(),
                        request.conversationId(), request.preset())));
    }

    @Operation(summary = "上传背景图", description = "JPEG/PNG/WebP，最大 8MB；`conversationId` 为空时改全局。")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChatBackgroundItem> upload(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(ChatBackgroundItem.from(
                chatBackgroundApplicationService.upload(user.userId(), conversationId,
                        file.getOriginalFilename(), file.getContentType(),
                        MultipartFiles.bytesOf(file))));
    }

    @Operation(summary = "清除背景", description = "会话级清除后回退到全局；`conversationId` 为空时清除全局。")
    @DeleteMapping
    public ApiResponse<ChatBackgroundItem> clear(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(value = "conversationId", required = false) String conversationId) {
        return ApiResponse.ok(ChatBackgroundItem.from(
                chatBackgroundApplicationService.clear(user.userId(), conversationId)));
    }
}
