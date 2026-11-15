package com.veryal.domain.friend;

import java.time.Instant;

/**
 * 好友申请。
 */
public record FriendRequest(long id, long tenantId, long fromUserId, long toUserId,
                            String message, FriendRequestStatus status, Instant createdAt,
                            Instant updatedAt) {

    public FriendRequest {
        if (id <= 0 || tenantId <= 0 || fromUserId <= 0 || toUserId <= 0) {
            throw new IllegalArgumentException("friend request ids must be positive");
        }
        if (fromUserId == toUserId) {
            throw new IllegalArgumentException("cannot request self");
        }
        if (status == null || createdAt == null) {
            throw new IllegalArgumentException("status/createdAt must not be null");
        }
        if (message != null && message.length() > 255) {
            message = message.substring(0, 255);
        }
    }
}
