package com.veryal.domain.moment;

/**
 * 朋友圈事件通知入口：MQ 关闭时本机直推，开启时由消费者广播。
 */
public interface MomentNotifier {

    void notify(MomentEvent event);
}
