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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FCM HTTP v1 真实推送。需配置项目 ID 与 Service Account JSON。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "veryal.push", name = "provider", havingValue = "fcm")
public class FcmOfflinePushProvider implements OfflinePushProvider {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final FcmAccessTokenProvider tokenProvider;
    private final String endpoint;

    public FcmOfflinePushProvider(@Value("${veryal.push.fcm.project-id:}") String projectId,
                                  @Value("${veryal.push.fcm.service-account-json:}") String serviceAccountJson) {
        if (projectId == null || projectId.isBlank() || serviceAccountJson == null
                || serviceAccountJson.isBlank()) {
            throw new IllegalArgumentException(
                    "veryal.push.fcm.project-id and service-account-json are required");
        }
        this.tokenProvider = new FcmAccessTokenProvider(serviceAccountJson);
        this.endpoint = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
    }

    @Override
    public void push(OfflinePushMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", message.token());
        payload.put("notification", Map.of("title", message.title(), "body", message.body()));
        payload.put("data", message.data());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + tokenProvider.token())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtils.toJson(Map.of("message", payload))))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("FCM push failed: status={}, body={}", response.statusCode(),
                        response.body());
            }
        } catch (Exception e) {
            log.warn("FCM push failed: userId={}, deviceId={}", message.userId(),
                    message.deviceId(), e);
        }
    }
}
