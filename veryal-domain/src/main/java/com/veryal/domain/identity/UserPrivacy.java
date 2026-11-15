package com.veryal.domain.identity;

import java.time.Instant;

/**
 * 用户隐私设置。
 */
public record UserPrivacy(long userId, boolean searchByPhone, boolean searchByUsername,
                          FriendPolicy friendPolicy, boolean showOnline, boolean readReceipt,
                          boolean invitedToGroup, Instant updatedAt) {

    public static UserPrivacy defaults(long userId, Instant now) {
        return new UserPrivacy(userId, true, true, FriendPolicy.VERIFY, true, true, true, now);
    }

    public UserPrivacy update(boolean searchByPhone, boolean searchByUsername,
                              FriendPolicy friendPolicy, boolean showOnline, boolean readReceipt,
                              boolean invitedToGroup, Instant now) {
        return new UserPrivacy(userId, searchByPhone, searchByUsername, friendPolicy, showOnline,
                readReceipt, invitedToGroup, now);
    }

    public enum FriendPolicy {
        REJECT(0),
        VERIFY(1),
        DIRECT(2);

        private final int code;

        FriendPolicy(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static FriendPolicy fromCode(int code) {
            for (FriendPolicy policy : values()) {
                if (policy.code == code) {
                    return policy;
                }
            }
            return VERIFY;
        }
    }
}
