package com.veryal.access.core.push;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 连接级推送去重。
 *
 * <p>MQ 开启时同一条消息会走两条链路：本节点直推（保证零延迟）与出箱队列
 * 路由到网关（保证可靠投递）。两条链路都会命中同一批连接，若不共享去重表
 * 客户端会收到重复消息，因此这里按 {@code eventId + connectionId} 做一次性占领。</p>
 *
 * <p>只保留短时间窗口：重复投递只会在同一事件的两条链路之间发生，超过窗口的
 * 重投由客户端按 messageId 兜底。字典按 TTL 与容量上限清理，避免长期运行膨胀。</p>
 */
@Component
public class ConnectionPushDedup {

    private static final long TTL_MS = 60_000L;
    private static final int MAX_ENTRIES = 500_000;

    private final ConcurrentMap<String, Long> seen = new ConcurrentHashMap<>();

    /**
     * 抢占某连接的本次投递权。
     *
     * @return {@code true} 表示第一次投递，可以发送；{@code false} 表示已有链路发过。
     */
    public boolean firstDelivery(String eventId, String connectionId) {
        if (eventId == null || connectionId == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        boolean first = seen.putIfAbsent(eventId + '|' + connectionId, now) == null;
        if (seen.size() > MAX_ENTRIES) {
            purge(now);
        }
        return first;
    }

    private void purge(long now) {
        long threshold = now - TTL_MS;
        seen.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }
}
