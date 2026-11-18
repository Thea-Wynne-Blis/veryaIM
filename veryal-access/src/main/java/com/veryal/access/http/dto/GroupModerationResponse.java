package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.group.GroupModerationService;

import java.time.Instant;

/**
 * 群公告/禁言状态。
 */
public record GroupModerationResponse(
        @Schema(description = "群 ID，等同于该群的会话 ID")
        String groupId,
        @Schema(description = "群公告内容")
        String announcement,
        @Schema(description = "是否开启全员禁言")
        boolean muteAll,
        @Schema(description = "我的禁言到期时间；永久禁言为 null")
        Instant mutedUntil,
        @Schema(description = "我是否被永久禁言")
        boolean mutedPermanently
) {

    public static GroupModerationResponse from(GroupModerationService.ModerationView view) {
        return new GroupModerationResponse(view.groupId(), view.announcement(), view.muteAll(),
                view.mutedUntil(), view.mutedPermanently());
    }
}
