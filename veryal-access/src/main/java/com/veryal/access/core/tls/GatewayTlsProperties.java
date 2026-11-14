package com.veryal.access.core.tls;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Netty TCP / WebSocket TLS 配置。
 *
 * <p>证书使用 PEM 格式（cert-path 为证书链，key-path 为私钥）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.gateway.tls")
public class GatewayTlsProperties {

    private boolean enabled;

    private String certPath;

    private String keyPath;

    private String keyPassword;
}
