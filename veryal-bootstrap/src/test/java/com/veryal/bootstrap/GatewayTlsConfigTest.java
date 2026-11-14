package com.veryal.bootstrap;

import com.veryal.access.core.tls.GatewaySslContextFactory;
import com.veryal.access.core.tls.GatewayTlsProperties;
import com.veryal.common.exception.SystemException;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayTlsConfigTest {

    @Test
    void disabledTlsReturnsNoHandler() {
        GatewayTlsProperties properties = new GatewayTlsProperties();
        GatewaySslContextFactory factory = new GatewaySslContextFactory(properties);
        assertThat(factory.newHandler(UnpooledByteBufAllocator.DEFAULT)).isNull();
    }

    @Test
    void enabledTlsWithoutCertFailsFast() {
        GatewayTlsProperties properties = new GatewayTlsProperties();
        properties.setEnabled(true);
        GatewaySslContextFactory factory = new GatewaySslContextFactory(properties);
        assertThatThrownBy(() -> factory.newHandler(UnpooledByteBufAllocator.DEFAULT))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("cert-path");
    }
}
