package com.veryal.access.core.tls;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * TLS 上下文工厂。
 *
 * <p>关闭时返回 {@code null}，接入层据此决定是否添加 SslHandler；开启但证书缺失时快速失败。</p>
 */
@Component
@RequiredArgsConstructor
public class GatewaySslContextFactory {

    private static final Logger log = LoggerFactory.getLogger(GatewaySslContextFactory.class);

    private final GatewayTlsProperties properties;

    private volatile SslContext sslContext;

    public SslHandler newHandler(ByteBufAllocator allocator) {
        if (!properties.isEnabled()) {
            return null;
        }
        return sslContext().newHandler(allocator);
    }

    private SslContext sslContext() {
        SslContext current = sslContext;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (sslContext == null) {
                sslContext = build();
            }
            return sslContext;
        }
    }

    private SslContext build() {
        File certFile = requireFile(properties.getCertPath(), "veryal.gateway.tls.cert-path");
        File keyFile = requireFile(properties.getKeyPath(), "veryal.gateway.tls.key-path");
        // 配置占位符默认值会绑定为空字符串；空密码必须归一化为 null，否则 PEM 会被当作加密私钥解析。
        String keyPassword = properties.getKeyPassword();
        if (keyPassword != null && keyPassword.isBlank()) {
            keyPassword = null;
        }
        try {
            SslContext context = SslContextBuilder.forServer(certFile, keyFile, keyPassword)
                    .protocols("TLSv1.3", "TLSv1.2")
                    .build();
            log.info("TLS enabled for gateways: cert={}, key={}", certFile, keyFile);
            return context;
        } catch (Exception e) {
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR, "failed to build TLS context", e);
        }
    }

    private File requireFile(String path, String propertyName) {
        if (path == null || path.isBlank()) {
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "TLS is enabled but " + propertyName + " is empty");
        }
        File file = new File(path);
        if (!file.isFile()) {
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "TLS file not found for " + propertyName + ": " + path);
        }
        return file;
    }
}
