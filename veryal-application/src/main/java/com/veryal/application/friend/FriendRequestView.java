package com.veryal.application.friend;

import com.veryal.domain.friend.FriendRequest;

import java.time.Instant;

/**
 * 好友申请视图（含双方昵称）。
 */
public record FriendRequestView(long requestId, long fromUserId, long toUserId, String fromNickname,
                                String toNickname, String message, String status, Instant createdAt) {

    public static FriendRequestView of(FriendRequest request, String fromNickname, String toNickname) {
        return new FriendRequestView(request.id(), request.fromUserId(), request.toUserId(),
                fromNickname, toNickname, request.message(), request.status().name(), request.createdAt());
    }
}
