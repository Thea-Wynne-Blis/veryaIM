package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.friend.FriendRequestView;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 好友申请响应。
 */
public record FriendRequestResponse(
                                 @Schema(description = "好友申请 ID")
                                 long requestId,
                                 @Schema(description = "申请人用户 ID")
                                 long fromUserId,
                                 @Schema(description = "被申请人用户 ID")
                                 long toUserId,
                                 String fromNickname,
                                 String toNickname,
                                 @Schema(description = "提示信息，可直接展示")
                                 String message,
                                 @Schema(description = "状态")
                                 String status,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt
) {

    public static FriendRequestResponse from(FriendRequestView view) {
        return new FriendRequestResponse(view.requestId(), view.fromUserId(), view.toUserId(),
                view.fromNickname(), view.toNickname(), view.message(), view.status(), view.createdAt());
    }
}
