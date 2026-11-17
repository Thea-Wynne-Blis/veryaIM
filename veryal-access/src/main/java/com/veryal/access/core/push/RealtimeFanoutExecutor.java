package com.veryal.access.core.push;

import com.veryal.access.core.config.RealtimeFanoutProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实时扇出执行器。
 *
 * <p>消息已经落库提交，实时推送只是「尽快送达」的优化，因此不在发送请求线程里做扇出：
 * 交给有界线程池执行，HTTP/WebSocket 发送方立即拿到响应。</p>
 *
 * <p>队列有界，满了丢弃最旧任务（与旧 SSE 的 {@code DiscardOldestPolicy} 一致）。
 * 被丢掉的推送由客户端轮询或按游标补齐，不会造成消息丢失；但内存不会无界增长。</p>
 */
@Slf4j
@Component
public class RealtimeFanoutExecutor {

    private final ThreadPoolExecutor executor;
    private final AtomicLong dropped = new AtomicLong();

    public RealtimeFanoutExecutor(RealtimeFanoutProperties properties) {
        int threads = Math.max(0, properties.getThreads());
        if (threads == 0) {
            this.executor = null;
            log.info("realtime fanout runs inline: threads=0");
            return;
        }
        int capacity = Math.max(1, properties.getQueueCapacity());
        AtomicInteger index = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "realtime-fanout-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), factory, (task, pool) -> {
                    // 队列已满：丢掉队首最旧任务，优先保证新消息的时效性。
                    pool.getQueue().poll();
                    long total = dropped.incrementAndGet();
                    if (total == 1 || total % 100 == 0) {
                        log.warn("realtime fanout queue full, dropped oldest task: droppedTotal={}", total);
                    }
                    try {
                        pool.execute(task);
                    } catch (RejectedExecutionException e) {
                        log.debug("realtime fanout rejected while dropping: {}", e.getMessage());
                    }
                });
        log.info("realtime fanout executor started: threads={}, queueCapacity={}", threads, capacity);
    }

    /**
     * 提交一次本节点扇出。
     *
     * <p>线程池关闭后直接丢弃：客户端重连或轮询时会从数据库补齐。</p>
     */
    public void submit(Runnable task) {
        if (executor == null) {
            task.run();
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            log.debug("realtime fanout rejected: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
