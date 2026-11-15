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

/**
 * 群公告与禁言。
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@RequiredArgsConstructor
public class GroupModerationController {

    private final GroupModerationService groupModerationService;

    @GetMapping("/moderation")
    public ApiResponse<GroupModerationResponse> get(@CurrentUser AuthenticatedUser user,
                                                    @PathVariable String groupId) {
        return ApiResponse.ok(GroupModerationResponse.from(
                groupModerationService.get(user.tenantId(), user.userId(), groupId)));
    }

    @PutMapping("/announcement")
    public ApiResponse<GroupModerationResponse> announcement(
            @CurrentUser AuthenticatedUser user, @PathVariable String groupId,
            @Valid @RequestBody GroupAnnouncementRequest request) {
        return ApiResponse.ok(GroupModerationResponse.from(
                groupModerationService.updateAnnouncement(user.tenantId(), user.userId(), groupId,
                        request.announcement())));
    }

    @PostMapping("/mute")
    public ApiResponse<GroupModerationResponse> muteAll(
            @CurrentUser AuthenticatedUser user, @PathVariable String groupId,
            @RequestBody GroupMuteAllRequest request) {
        return ApiResponse.ok(GroupModerationResponse.from(
                groupModerationService.setMuteAll(user.tenantId(), user.userId(), groupId,
                        request.muteAll())));
    }

    @PostMapping("/members/{userId}/mute")
    public ApiResponse<GroupModerationResponse> muteMember(
            @CurrentUser AuthenticatedUser user, @PathVariable String groupId,
            @PathVariable long userId, @Valid @RequestBody GroupMemberMuteRequest request) {
        return ApiResponse.ok(GroupModerationResponse.from(
                groupModerationService.muteMember(user.tenantId(), user.userId(), groupId, userId,
                        request.minutes())));
    }
}
