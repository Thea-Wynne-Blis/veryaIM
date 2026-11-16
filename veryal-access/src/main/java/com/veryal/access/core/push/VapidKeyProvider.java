package com.veryal.access.core.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.veryal.application.push.WebPushProperties;
import com.veryal.common.json.JsonUtils;
import com.veryal.common.security.JoseSignatures;
import com.veryal.common.security.WebPushCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VAPID 密钥提供者。
 *
 * <p>优先级：显式配置 → key-file 读取 → 自动生成并写入 key-file。
 * 自动生成的密钥会持久化，避免重启后已有订阅全部失效。</p>
 */
@Slf4j
@Component
public class VapidKeyProvider {

    private final WebPushProperties properties;
    private final byte[] privateScalar;
    private final byte[] publicPoint;

    private volatile KeyPair keyPair;

    public VapidKeyProvider(WebPushProperties properties) {
        this.properties = properties;
        byte[][] loaded = loadOrCreate();
        this.privateScalar = loaded[0];
        this.publicPoint = loaded[1];
    }

    public String publicKeyBase64Url() {
        return JoseSignatures.base64Url(publicPoint);
    }

    public PrivateKey privateKey() {
        return keyPair().getPrivate();
    }

    public KeyPair keyPair() {
        if (keyPair == null) {
            synchronized (this) {
                if (keyPair == null) {
                    keyPair = new KeyPair(WebPushCrypto.publicKeyFrom(publicPoint),
                            WebPushCrypto.privateKeyFrom(privateScalar));
                }
            }
        }
        return keyPair;
    }

    private byte[][] loadOrCreate() {
        if (!isBlank(properties.getPublicKey()) && !isBlank(properties.getPrivateKey())) {
            return new byte[][]{
                    JoseSignatures.base64UrlDecode(properties.getPrivateKey()),
                    JoseSignatures.base64UrlDecode(properties.getPublicKey())};
        }
        Path path = Path.of(properties.getKeyFile());
        if (Files.exists(path)) {
            try {
                Map<String, String> stored = JsonUtils.mapper().readValue(
                        Files.readString(path), new TypeReference<LinkedHashMap<String, String>>() {
                        });
                return new byte[][]{
                        JoseSignatures.base64UrlDecode(stored.get("privateKey")),
                        JoseSignatures.base64UrlDecode(stored.get("publicKey"))};
            } catch (Exception e) {
                throw new IllegalStateException("failed to read VAPID key file: " + path, e);
            }
        }
        KeyPair generated = WebPushCrypto.generateEcKeyPair();
        byte[] point = WebPushCrypto.encodePoint(
                ((ECPublicKey) generated.getPublic()).getW(), WebPushCrypto.ecParameterSpec());
        byte[] scalar = toScalar((java.security.interfaces.ECPrivateKey) generated.getPrivate());
        persist(path, point, scalar);
        log.info("generated new VAPID key pair at {}; publicKey={}", path,
                JoseSignatures.base64Url(point));
        return new byte[][]{scalar, point};
    }

    private void persist(Path path, byte[] point, byte[] scalar) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, String> content = new LinkedHashMap<>();
            content.put("publicKey", JoseSignatures.base64Url(point));
            content.put("privateKey", JoseSignatures.base64Url(scalar));
            Files.writeString(path, JsonUtils.toJson(content));
        } catch (Exception e) {
            throw new IllegalStateException("failed to persist VAPID key file: " + path, e);
        }
    }

    private byte[] toScalar(java.security.interfaces.ECPrivateKey key) {
        byte[] bytes = key.getS().toByteArray();
        byte[] scalar = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, scalar, 32 - copy, copy);
        return scalar;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
