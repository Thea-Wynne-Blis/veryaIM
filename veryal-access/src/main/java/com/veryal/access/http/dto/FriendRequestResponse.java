package com.veryal.access.http.dto;

import com.veryal.application.friend.FriendRequestView;

import java.time.Instant;

/**
 * 好友申请响应。
 */
public record FriendRequestResponse(long requestId, long fromUserId, long toUserId,
                                    String fromNickname, String toNickname, String message,
                                    String status, Instant createdAt) {

    public static FriendRequestResponse from(FriendRequestView view) {
        return new FriendRequestResponse(view.requestId(), view.fromUserId(), view.toUserId(),
                view.fromNickname(), view.toNickname(), view.message(), view.status(), view.createdAt());
    }
}
