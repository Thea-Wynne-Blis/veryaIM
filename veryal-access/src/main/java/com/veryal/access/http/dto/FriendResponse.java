package com.veryal.access.http.dto;

import com.veryal.application.friend.FriendView;

import java.time.Instant;

/**
 * 好友响应。
 */
public record FriendResponse(long friendId, String nickname, String avatar, String remark,
                             boolean online, Instant createdAt) {

    public static FriendResponse from(FriendView view) {
        return new FriendResponse(view.friendId(), view.nickname(), view.avatar(), view.remark(),
                view.online(), view.createdAt());
    }
}
