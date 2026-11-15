package com.veryal.bootstrap;

import com.veryal.access.core.config.GatewayExecutorConfig;
import com.veryal.access.core.config.GatewayProperties;
import com.veryal.access.core.config.VirtualThreadBusinessExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VirtualThreadExecutorTest {

    @Test
    void boundsConcurrencyAndReleasesPermits() throws Exception {
        VirtualThreadBusinessExecutor executor = new VirtualThreadBusinessExecutor(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(RejectedExecutionException.class);

            release.countDown();
            Thread.sleep(100);
            executor.execute(() -> { });
            assertThat(executor.availablePermits()).isGreaterThanOrEqualTo(0);
        } finally {
            release.countDown();
            executor.close();
        }
    }

    @Test
    void configSelectsVirtualExecutor() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBusinessExecutor("virtual");
        properties.setBusinessConcurrency(4);
        Executor executor = new GatewayExecutorConfig().gatewayBusinessExecutor(properties);
        try {
            assertThat(executor).isInstanceOf(VirtualThreadBusinessExecutor.class);
            executor.execute(() -> { });
        } finally {
            ((VirtualThreadBusinessExecutor) executor).shutdown();
        }
    }
}
