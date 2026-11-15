package com.veryal.access.core.push;

import com.veryal.common.json.JsonUtils;
import com.veryal.domain.push.OfflinePushMessage;
import com.veryal.domain.push.OfflinePushProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * APNs HTTP/2 + ES256 JWT 真实推送。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "veryal.push", name = "provider", havingValue = "apns")
public class ApnsOfflinePushProvider implements OfflinePushProvider {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String teamId;
    private final String keyId;
    private final String topic;
    private final boolean production;
    private final PrivateKey privateKey;

    private volatile String jwt;
    private volatile Instant jwtIssuedAt = Instant.EPOCH;

    public ApnsOfflinePushProvider(@Value("${veryal.push.apns.team-id:}") String teamId,
                                   @Value("${veryal.push.apns.key-id:}") String keyId,
                                   @Value("${veryal.push.apns.private-key-path:}") String keyPath,
                                   @Value("${veryal.push.apns.topic:}") String topic,
                                   @Value("${veryal.push.apns.production:false}") boolean production) {
        if (isBlank(teamId) || isBlank(keyId) || isBlank(keyPath) || isBlank(topic)) {
            throw new IllegalArgumentException("APNs team-id/key-id/private-key-path/topic are required");
        }
        this.teamId = teamId;
        this.keyId = keyId;
        this.topic = topic;
        this.production = production;
        try {
            String pem = Files.readString(Path.of(keyPath))
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            this.privateKey = KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load APNs private key", e);
        }
    }

    @Override
    public void push(OfflinePushMessage message) {
        try {
            Map<String, Object> aps = new LinkedHashMap<>();
            aps.put("alert", Map.of("title", message.title(), "body", message.body()));
            aps.put("sound", "default");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("aps", aps);
            payload.putAll(message.data());
            String host = production ? "https://api.push.apple.com" : "https://api.sandbox.push.apple.com";
            HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/3/device/" + message.token()))
                    .timeout(Duration.ofSeconds(10))
                    .header("authorization", "bearer " + token())
                    .header("apns-topic", topic)
                    .header("apns-push-type", "alert")
                    .header("apns-priority", "10")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("APNs push failed: status={}, body={}", response.statusCode(),
                        response.body());
            }
        } catch (Exception e) {
            log.warn("APNs push failed: userId={}, deviceId={}", message.userId(),
                    message.deviceId(), e);
        }
    }

    private String token() throws Exception {
        if (jwt != null && Instant.now().isBefore(jwtIssuedAt.plusSeconds(3000))) {
            return jwt;
        }
        long now = Instant.now().getEpochSecond();
        String header = encode(("{\"alg\":\"ES256\",\"kid\":\"" + keyId + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        String claims = encode(JsonUtils.toJson(Map.of("iss", teamId, "iat", now))
                .getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update((header + "." + claims).getBytes(StandardCharsets.UTF_8));
        jwt = header + "." + claims + "." + encode(toJoseSignature(signature.sign()));
        jwtIssuedAt = Instant.now();
        return jwt;
    }

    /**
     * Java ECDSA 返回 DER，JOSE 需要 32 字节 R || 32 字节 S。
     */
    private static byte[] toJoseSignature(byte[] der) {
        int offset = 2;
        if ((der[1] & 0xFF) > 0x7F) {
            offset = 3;
        }
        int rLength = der[offset + 1] & 0xFF;
        int rOffset = offset + 2;
        int sLength = der[rOffset + rLength + 1] & 0xFF;
        int sOffset = rOffset + rLength + 2;
        byte[] result = new byte[64];
        copyPadded(der, rOffset, rLength, result, 0);
        copyPadded(der, sOffset, sLength, result, 32);
        return result;
    }

    private static void copyPadded(byte[] source, int offset, int length, byte[] target,
                                   int targetOffset) {
        int copy = Math.min(length, 32);
        int sourceStart = offset + length - copy;
        int targetStart = targetOffset + 32 - copy;
        System.arraycopy(source, sourceStart, target, targetStart, copy);
    }

    private static String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
