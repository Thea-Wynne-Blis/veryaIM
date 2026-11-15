package com.veryal.application.friend;

import java.time.Instant;

/**
 * 好友视图。
 */
public record FriendView(long friendId, String nickname, String avatar, String remark,
                         boolean online, Instant createdAt) {
}
