package com.veryal.access.core.push;

import com.veryal.application.push.WebPushProperties;
import com.veryal.common.json.JsonUtils;
import com.veryal.common.security.JoseSignatures;
import com.veryal.common.security.WebPushCrypto;
import com.veryal.domain.push.WebPushPayload;
import com.veryal.domain.push.WebPushSender;
import com.veryal.domain.push.WebPushSubscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web Push 真实发送：RFC 8291 加密 + VAPID（RFC 8292）签名 + HTTP 投递。
 */
@Slf4j
@Component
public class WebPushSenderImpl implements WebPushSender {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration JWT_TTL = Duration.ofHours(12);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final VapidKeyProvider keys;
    private final WebPushProperties properties;

    private volatile String cachedJwt;
    private volatile Instant cachedJwtExpiry = Instant.EPOCH;

    public WebPushSenderImpl(VapidKeyProvider keys, WebPushProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    @Override
    public String vapidPublicKey() {
        return keys.publicKeyBase64Url();
    }

    @Override
    public boolean send(WebPushSubscription subscription, WebPushPayload payload) {
        try {
            URI endpoint = URI.create(subscription.endpoint());
            byte[] uaPublic = JoseSignatures.base64UrlDecode(subscription.p256dh());
            byte[] authSecret = JoseSignatures.base64UrlDecode(subscription.auth());

            byte[] body = WebPushCrypto.encrypt(toJson(payload), uaPublic, authSecret);

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", authorization(endpoint))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", Long.toString(Math.max(1, properties.getTtlSeconds())))
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                return true;
            }
            if (status >= 300) {
                log.warn("web push rejected: status={}, userId={}", status,
                        subscription.userId());
            }
            return false;
        } catch (Exception e) {
            log.warn("web push send failed: userId={}, subscriptionId={}",
                    subscription.userId(), subscription.id(), e);
            return false;
        }
    }

    private String authorization(URI endpoint) {
        String jwt = vapidJwt(endpoint);
        return "vapid t=" + jwt + ", k=" + keys.publicKeyBase64Url();
    }

    private String vapidJwt(URI endpoint) {
        Instant now = Instant.now();
        if (cachedJwt != null && now.isBefore(cachedJwtExpiry)) {
            return cachedJwt;
        }
        try {
            String header = JoseSignatures.base64Url(
                    "{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("aud", origin(endpoint));
            claims.put("exp", now.plus(JWT_TTL).getEpochSecond());
            claims.put("sub", properties.getSubject());
            String payload = JoseSignatures.base64Url(
                    JsonUtils.toJson(claims).getBytes(StandardCharsets.UTF_8));

            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keys.privateKey());
            signature.update((header + "." + payload).getBytes(StandardCharsets.UTF_8));
            String jwt = header + "." + payload + "."
                    + JoseSignatures.base64Url(JoseSignatures.derToJose(signature.sign()));

            cachedJwt = jwt;
            cachedJwtExpiry = now.plus(JWT_TTL).minusSeconds(300);
            return jwt;
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign VAPID JWT", e);
        }
    }

    private String origin(URI endpoint) {
        String scheme = endpoint.getScheme();
        String host = endpoint.getHost();
        int port = endpoint.getPort();
        int defaultPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        if (port == -1 || port == defaultPort) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    private byte[] toJson(WebPushPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", payload.title());
        body.put("body", payload.body());
        body.put("data", payload.data());
        return JsonUtils.toJsonBytes(body);
    }
}
