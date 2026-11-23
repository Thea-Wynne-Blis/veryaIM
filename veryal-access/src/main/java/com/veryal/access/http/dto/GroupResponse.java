package com.veryal.access.http.dto;

import com.veryal.application.group.GroupSummary;
import com.veryal.application.group.GroupView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
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
        @Schema(description = "群头像地址；未设置时返回 404，客户端应回退到文字头像")
        String avatar,
        @Schema(description = "成员用户 ID 列表")
        List<Long> memberIds,
        @Schema(description = "创建时间（UTC）")
        Instant createdAt,
        @Schema(description = "更新时间（UTC）")
        Instant updatedAt,
        @Schema(description = "我在群内的角色：OWNER 群主 / ADMIN 管理员 / MEMBER 成员")
        String roleInGroup
) {

    public static GroupResponse from(GroupView view) {
        return new GroupResponse(view.groupId(), view.name(), view.ownerId(),
                avatarUrl(view.groupId()), view.memberIds(),
                view.createdAt(), view.updatedAt(), view.roleInGroup());
    }

    public static GroupResponse from(GroupSummary summary) {
        return new GroupResponse(summary.groupId(), summary.name(), summary.ownerId(),
                avatarUrl(summary.groupId()), List.of(),
                null, summary.updatedAt(), null);
    }

    /** 头像地址由群 ID 推导，未设置时接口返回 404，客户端回退到文字头像。 */
    private static String avatarUrl(String groupId) {
        return "/api/v1/avatars/group/" + groupId;
    }
}
