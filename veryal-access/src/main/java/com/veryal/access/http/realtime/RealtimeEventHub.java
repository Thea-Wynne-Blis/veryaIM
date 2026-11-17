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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网页端实时事件中心（SSE）。
 *
 * <p>每个登录用户可订阅一个长连接；新消息落库后立即推送事件，前端据此增量刷新，
 * 避免固定轮询带来的秒级延迟。</p>
 *
 * <p>推送在独立线程池执行：某个慢连接/半死连接不会拖慢发送请求线程；
 * 队列打满时丢弃最旧任务（客户端重连后按 seq 补齐），绝不让缓冲区无限增长。</p>
 */
@Slf4j
@Component
public class RealtimeEventHub implements RealtimeStreamPort {

    private static final long HEARTBEAT_MS = 15_000L;
    private static final int PUSH_THREADS = 2;
    private static final int PUSH_QUEUE_CAPACITY = 10_000;

    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService pushExecutor;

    public RealtimeEventHub() {
        AtomicInteger index = new AtomicInteger();
        this.pushExecutor = new ThreadPoolExecutor(PUSH_THREADS, PUSH_THREADS,
                60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(PUSH_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "realtime-push-" + index.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

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
        Set<Long> userIds = new LinkedHashSet<>();
        if (event.recipientUserIds() != null) {
            userIds.addAll(event.recipientUserIds());
        }
        userIds.add(event.senderId());
        if (userIds.isEmpty()) {
            return;
        }
        boolean fullPayload = event.recipientUserIds() == null
                || event.recipientUserIds().size() <= RealtimeNotice.FULL_PAYLOAD_MAX_RECIPIENTS;
        RealtimeNotice notice = fullPayload
                ? RealtimeNotice.message(event)
                : RealtimeNotice.cursor(event);
        for (Long userId : userIds) {
            if (!subscribers.containsKey(userId)) {
                continue;
            }
            pushExecutor.execute(() -> send(userId, "message", notice));
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
        } catch (Exception ignored) {
            // 客户端已断开时 complete() 会抛 AsyncRequestNotUsableException（IOException 子类），
            // 属于正常断开，不能让它冒到全局异常处理器刷一条 ERROR 堆栈
        }
    }
}
