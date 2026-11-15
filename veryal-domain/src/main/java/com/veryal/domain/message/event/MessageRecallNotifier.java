package com.veryal.domain.message.event;

/**
 * 撤回通知入口：MQ 关闭时由 access 本机直推，MQ 开启时为空实现（由消费者广播）。
 */
public interface MessageRecallNotifier {

    void notify(MessageRecalledEvent event);
}
