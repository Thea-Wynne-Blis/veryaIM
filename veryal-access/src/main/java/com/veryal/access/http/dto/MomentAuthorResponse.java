package com.veryal.access.http.dto;

import com.veryal.application.moment.MomentAuthorView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 动态作者 / 互动用户。
 */
public record MomentAuthorResponse(
        @Schema(description = "用户 ID") long userId,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "头像地址") String avatar,
        @Schema(description = "好友备注；为空时使用昵称") String remark
) {

    public static MomentAuthorResponse from(MomentAuthorView view) {
        return new MomentAuthorResponse(view.userId(), view.nickname(), view.avatar(),
                view.remark());
    }
}
