package com.veryal.access.core.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * 虚拟线程业务执行器。
 *
 * <p>每个任务一个虚拟线程，适合 JDBC/Redis/RabbitMQ 等等待型 IO；用信号量限制并发，
 * 避免无限并发压垮连接池，同时保留拒绝语义（队列满时返回 SERVICE_UNAVAILABLE）。</p>
 */
public final class VirtualThreadBusinessExecutor implements Executor, AutoCloseable {

    private final Semaphore permits;
    private final ExecutorService delegate;

    public VirtualThreadBusinessExecutor(int maxConcurrency) {
        this.permits = new Semaphore(Math.max(1, maxConcurrency));
        this.delegate = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void execute(Runnable command) {
        if (!permits.tryAcquire()) {
            throw new RejectedExecutionException("virtual business executor is saturated");
        }
        try {
            delegate.execute(() -> {
                try {
                    command.run();
                } finally {
                    permits.release();
                }
            });
        } catch (RuntimeException e) {
            permits.release();
            throw e;
        }
    }

    public int availablePermits() {
        return permits.availablePermits();
    }

    public void shutdown() {
        close();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
