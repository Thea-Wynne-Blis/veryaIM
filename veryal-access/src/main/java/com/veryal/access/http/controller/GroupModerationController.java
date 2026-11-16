package com.veryal.access.http.controller;

import com.veryal.access.http.dto.GroupAnnouncementRequest;
import com.veryal.access.http.dto.GroupMemberMuteRequest;
import com.veryal.access.http.dto.GroupModerationResponse;
import com.veryal.access.http.dto.GroupMuteAllRequest;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.group.GroupModerationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 群公告与禁言。
 */
@Tag(name = "群聊")
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@RequiredArgsConstructor
public class GroupModerationController {

    private final GroupModerationService groupModerationService;

    @Operation(summary = "查询群管控状态", description = "返回当前公告、全员禁言状态与成员禁言列表。")
    @GetMapping("/moderation")
    public ApiResponse<GroupModerationResponse> get(@CurrentUser AuthenticatedUser user,
                                                    @PathVariable String groupId) {
        return ApiResponse.ok(GroupModerationResponse.from(
                groupModerationService.get(user.userId(), groupId)));
    }

    @Operation(summary = "发布群公告", description = "群主或管理员可发布，公告展示在会话详情面板。")
    @PutMapping("/announcement")
    public ApiResponse<GroupModerationResponse> announcement(
            @CurrentUser AuthenticatedUser user, @PathVariable String groupId,
            @Valid @RequestBody GroupAnnouncementRequest request) {
        return ApiResponse.ok(GroupModerationResponse.from(
                groupModerationService.updateAnnouncement(user.userId(), groupId,
                        request.announcement())));
    }

    @Operation(summary = "全员禁言", description = "开启后仅群主与管理员可发言。")
    @PostMapping("/mute")
    public ApiResponse<GroupModerationResponse> muteAll(
            @CurrentUser AuthenticatedUser user, @PathVariable String groupId,
            @RequestBody GroupMuteAllRequest request) {
        return ApiResponse.ok(GroupModerationResponse.from(
                groupModerationService.setMuteAll(user.userId(), groupId,
                        request.muteAll())));
    }

    @Operation(summary = "禁言单个成员", description = "对指定成员单独禁言，可设置时长。")
    @PostMapping("/members/{userId}/mute")
    public ApiResponse<GroupModerationResponse> muteMember(
            @CurrentUser AuthenticatedUser user, @PathVariable String groupId,
            @PathVariable long userId, @Valid @RequestBody GroupMemberMuteRequest request) {
        return ApiResponse.ok(GroupModerationResponse.from(
                groupModerationService.muteMember(user.userId(), groupId, userId,
                        request.minutes())));
    }
}
