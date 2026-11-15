package com.veryal.access.http.dto;

import com.veryal.application.group.GroupModerationService;

import java.time.Instant;

/**
 * 群公告/禁言状态。
 */
public record GroupModerationResponse(String groupId, String announcement, boolean muteAll,
                                      Instant mutedUntil) {

    public static GroupModerationResponse from(GroupModerationService.ModerationView view) {
        return new GroupModerationResponse(view.groupId(), view.announcement(), view.muteAll(),
                view.mutedUntil());
    }
}
