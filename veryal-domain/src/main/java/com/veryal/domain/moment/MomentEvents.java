package com.veryal.domain.moment;

/**
 * 朋友圈事件类型（Outbox event_type / routing key）。
 */
public final class MomentEvents {

    public static final String NOTIFY = "moment.notify";

    private MomentEvents() {
    }
}
