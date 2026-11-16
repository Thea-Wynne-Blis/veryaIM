package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.group.GroupSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.group.GroupView;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 群详情响应。
 */
public record GroupResponse(
                                 @Schema(description = "群 ID，等同于该群的会话 ID")
                                 String groupId,
                                 @Schema(description = "名称")
                                 String name,
                                 @Schema(description = "群主用户 ID")
                                 long ownerId,
                                 @Schema(description = "成员用户 ID 列表")
                                 List<Long> memberIds,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt,
                                 @Schema(description = "更新时间（UTC）")
                                 Instant updatedAt
) {

    public static GroupResponse from(GroupView view) {
        return new GroupResponse(view.groupId(), view.name(), view.ownerId(), view.memberIds(),
                view.createdAt(), view.updatedAt());
    }

    public static GroupResponse from(GroupSummary summary) {
        return new GroupResponse(summary.groupId(), summary.name(), summary.ownerId(), List.of(),
                null, summary.updatedAt());
    }
}
