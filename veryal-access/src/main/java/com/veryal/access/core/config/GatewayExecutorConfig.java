package com.veryal.access.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务执行器：平台线程池或虚拟线程执行器，二者都保持有界并发。
 *
 * <p>Netty EventLoop 始终使用平台线程，业务 Handler 不直接在 EventLoop 上执行阻塞操作。</p>
 */
@Configuration
public class GatewayExecutorConfig {

    public static final String BUSINESS_EXECUTOR = "gatewayBusinessExecutor";

    @Bean(name = BUSINESS_EXECUTOR, destroyMethod = "shutdown")
    public Executor gatewayBusinessExecutor(GatewayProperties properties) {
        if ("virtual".equalsIgnoreCase(properties.getBusinessExecutor())) {
            return new VirtualThreadBusinessExecutor(properties.getBusinessConcurrency());
        }
        int threads = Math.max(1, properties.getBusinessThreads());
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "veryal-biz-" + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads,
                threads * 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(1, properties.getBusinessQueueCapacity())),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }
}
