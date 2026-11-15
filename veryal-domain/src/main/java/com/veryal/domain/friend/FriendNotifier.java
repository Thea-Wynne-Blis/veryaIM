package com.veryal.domain.friend;

/**
 * 好友事件通知入口：MQ 关闭时本机直推，开启时由消费者广播。
 */
public interface FriendNotifier {

    void notify(FriendEvent event);
}
