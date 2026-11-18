package com.veryal.application.friend;

import java.time.Instant;

/**
 * 好友视图。
 *
 * @param createdAt 成为好友的时间，来自 im_friend.created_at
 */
public record FriendView(long friendId, String nickname, String avatar,
                         String region, String signature, String remark,
                         boolean online, Instant createdAt) {
}
