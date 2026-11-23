package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.group.GroupSummary;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 群列表项响应。
 */
public record GroupSummaryResponse(
                                 @Schema(description = "群 ID，等同于该群的会话 ID")
                                 String groupId,
                                 @Schema(description = "名称")
                                 String name,
                                 @Schema(description = "群主用户 ID")
                                 long ownerId,
                                 @Schema(description = "群头像地址；未设置时返回 404，客户端应回退到文字头像")
                                 String avatar,
                                 @Schema(description = "成员数量")
                                 long memberCount,
                                 @Schema(description = "更新时间（UTC）")
                                 Instant updatedAt
) {

    public static GroupSummaryResponse from(GroupSummary summary) {
        return new GroupSummaryResponse(summary.groupId(), summary.name(), summary.ownerId(),
                "/api/v1/avatars/group/" + summary.groupId(),
                summary.memberCount(), summary.updatedAt());
    }
}
