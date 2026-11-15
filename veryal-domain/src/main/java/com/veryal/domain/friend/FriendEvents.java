package com.veryal.domain.friend;

/**
 * 好友事件类型（Outbox event_type / routing key）。
 */
public final class FriendEvents {

    public static final String NOTIFY = "friend.notify";

    private FriendEvents() {
    }
}
