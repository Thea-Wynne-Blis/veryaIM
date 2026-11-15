package com.veryal.domain.message.event;

/**
 * 撤回通知推送端口。
 */
public interface MessageRecallPushPort {

    void push(MessageRecalledEvent event);
}
