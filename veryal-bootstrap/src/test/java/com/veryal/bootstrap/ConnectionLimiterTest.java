package com.veryal.bootstrap;

import com.veryal.access.core.config.GatewayProperties;
import com.veryal.access.core.limit.ConnectionLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionLimiterTest {

    @Test
    void enforcesGlobalAndPerIpLimits() {
        GatewayProperties properties = new GatewayProperties();
        properties.setMaxConnections(2);
        properties.setMaxConnectionsPerIp(1);
        ConnectionLimiter limiter = new ConnectionLimiter(properties);

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.3")).isFalse();
        assertThat(limiter.activeCount()).isEqualTo(2);

        limiter.release("10.0.0.1");
        assertThat(limiter.tryAcquire("10.0.0.3")).isTrue();
        assertThat(limiter.activeCount()).isEqualTo(2);
    }

    @Test
    void zeroMeansUnlimited() {
        ConnectionLimiter limiter = new ConnectionLimiter(new GatewayProperties());
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        }
        assertThat(limiter.activeCount()).isEqualTo(100);
    }
}
