package com.veryal.domain.friend;

/**
 * 好友事件本节点推送端口。
 */
public interface FriendPushPort {

    void push(FriendEvent event);
}
