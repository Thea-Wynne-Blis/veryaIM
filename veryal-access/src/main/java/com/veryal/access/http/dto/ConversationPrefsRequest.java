package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 会话列表的成员级设置请求。
 *
 * <p>字段全部可选：只提交要改的那一项，其余保持原值。
 * 这些设置只作用于当前用户，不影响会话里的其他人。</p>
 */
public record ConversationPrefsRequest(
        @Schema(description = "是否置顶")
        Boolean pinned,
        @Schema(description = "是否开启消息免打扰")
        Boolean muted,
        @Schema(description = "是否设为「不显示」（收到新消息会自动恢复展示）")
        Boolean hidden,
        @Schema(description = "是否手动标为未读")
        Boolean unread
) {
}
