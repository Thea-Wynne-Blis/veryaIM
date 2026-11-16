package com.veryal.access.http.realtime;

import com.veryal.domain.delivery.RealtimeStreamPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页端实时事件中心（SSE）。
 *
 * <p>每个登录用户可订阅一个长连接；新消息落库后立即推送"有新消息"事件，
 * 前端据此增量拉取该会话，避免固定轮询带来的秒级延迟。</p>
 */
@Slf4j
@Component
public class RealtimeEventHub implements RealtimeStreamPort {

    private static final long HEARTBEAT_MS = 15_000L;

    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> unsubscribe(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("ready")
                    .data(Map.of("userId", Long.toString(userId)), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            cleanup.run();
        }
        return emitter;
    }

    /** 新消息：推给全部会话成员（含发送者的其他设备）。 */
    @Override
    public void publish(MessageCreatedEvent event) {
        RealtimeNotice notice = RealtimeNotice.message(event);
        Set<Long> userIds = new LinkedHashSet<>(event.recipientUserIds());
        userIds.add(event.senderId());
        for (Long userId : userIds) {
            send(userId, "message", notice);
        }
    }

    /** 心跳注释，防止代理层与浏览器因空闲断开长连接。 */
    @Scheduled(fixedDelay = HEARTBEAT_MS)
    public void heartbeat() {
        subscribers.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    unsubscribe(userId, emitter);
                }
            }
        });
    }

    private void send(long userId, String eventName, Object data) {
        Set<SseEmitter> emitters = subscribers.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                log.debug("realtime stream closed: userId={}", userId);
                unsubscribe(userId, emitter);
            }
        }
    }

    private void unsubscribe(long userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = subscribers.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(userId);
        }
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            /* 已结束的连接重复关闭可忽略 */
        }
    }
}
