package com.veryal.infrastructure.mq;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 消费端幂等去重：同一 eventId 在窗口期内只投递一次。
 *
 * <p>MQ 语义为至少一次投递，去重窗口只是减少重复推送；客户端仍按 messageId 去重。</p>
 */
@Component
public class DeliveryDeduplicator {

    private final Cache<String, Boolean> seenEvents = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public boolean firstSeen(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        return seenEvents.asMap().putIfAbsent(eventId, Boolean.TRUE) == null;
    }
}
