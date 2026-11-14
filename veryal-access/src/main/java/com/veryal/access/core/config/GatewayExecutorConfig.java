package com.veryal.access.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务线程池。网络 EventLoop 与业务阻塞操作严格隔离；线程池必须有界。
 */
@Configuration
public class GatewayExecutorConfig {

    public static final String BUSINESS_EXECUTOR = "gatewayBusinessExecutor";

    @Bean(name = BUSINESS_EXECUTOR, destroyMethod = "shutdown")
    public ThreadPoolExecutor gatewayBusinessExecutor(GatewayProperties properties) {
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
