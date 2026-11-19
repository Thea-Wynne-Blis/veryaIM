package com.veryal.domain.moment;

/**
 * 朋友圈事件本节点推送端口。
 */
public interface MomentPushPort {

    void push(MomentEvent event);
}
