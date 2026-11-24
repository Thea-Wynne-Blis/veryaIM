package com.veryal.access.core.push.offline;

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
import java.time.Duration;
import java.util.Map;

/**
 * 通用厂商推送网关适配器：POST webhook，由网关再调用 APNs/FCM/华为/小米等。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "veryal.push", name = "provider", havingValue = "webhook")
public class WebhookOfflinePushProvider implements OfflinePushProvider {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final String webhookUrl;
    private final String webhookToken;

    public WebhookOfflinePushProvider(@Value("${veryal.push.webhook-url:}") String webhookUrl,
                                      @Value("${veryal.push.webhook-token:}") String webhookToken) {
        this.webhookUrl = webhookUrl;
        this.webhookToken = webhookToken;
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("veryal.push.webhook-url is required for webhook provider");
        }
    }

    @Override
    public void push(OfflinePushMessage message) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(Map.of(
                            "userId", message.userId(),
                            "deviceId", message.deviceId(),
                            "platform", message.platform().name(),
                            "token", message.token(),
                            "title", message.title(),
                            "body", message.body(),
                            "data", message.data()))));
            if (webhookToken != null && !webhookToken.isBlank()) {
                builder.header("Authorization", "Bearer " + webhookToken);
            }
            HttpResponse<Void> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                log.warn("offline push webhook returned {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("offline push webhook failed: userId={}", message.userId(), e);
        }
    }
}
