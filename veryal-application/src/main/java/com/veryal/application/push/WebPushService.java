package com.veryal.application.push;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.push.WebPushPayload;
import com.veryal.domain.push.WebPushSender;
import com.veryal.domain.push.WebPushSubscription;
import com.veryal.domain.push.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * 浏览器 Web Push 订阅与发送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    private final WebPushSubscriptionRepository subscriptionRepository;
    private final WebPushSender webPushSender;
    private final WebPushProperties properties;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * 前端订阅所需配置；关闭时 {@code enabled=false} 且不返回公钥。
     */
    public WebPushConfigView config() {
        if (!properties.isEnabled()) {
            return new WebPushConfigView(false, null);
        }
        return new WebPushConfigView(true, webPushSender.vapidPublicKey());
    }

    @Transactional
    public void subscribe(long userId, String endpoint, String p256dh, String auth,
                          String userAgent) {
        requireEnabled();
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 2048) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid push endpoint");
        }
        if (p256dh == null || p256dh.isBlank() || auth == null || auth.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid push keys");
        }
        String hash = sha256(endpoint);
        List<WebPushSubscription> existing = subscriptionRepository.listEnabled(userId);
        boolean known = existing.stream().anyMatch(item -> item.endpointHash().equals(hash));
        if (!known && existing.size() >= Math.max(1, properties.getMaxSubscriptionsPerUser())) {
            throw new BusinessException(ErrorCode.CONFLICT, "too many push subscriptions");
        }
        Instant now = timeProvider.now();
        WebPushSubscription current = subscriptionRepository.findByEndpointHash(hash).orElse(null);
        subscriptionRepository.upsert(new WebPushSubscription(
                current == null ? idGenerator.nextId() : current.id(), userId, endpoint.trim(),
                hash, p256dh, auth, truncate(userAgent), true,
                current == null ? now : current.createdAt(), now));
    }

    @Transactional
    public void unsubscribe(long userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid push endpoint");
        }
        subscriptionRepository.findByEndpointHash(sha256(endpoint))
                .filter(item -> item.userId() == userId)
                .ifPresent(item -> subscriptionRepository.delete(item.endpointHash()));
    }

    /**
     * 向该用户所有启用订阅推送；失效订阅自动清理。
     */
    public void push(long userId, WebPushPayload payload) {
        if (!properties.isEnabled()) {
            return;
        }
        for (WebPushSubscription subscription : subscriptionRepository.listEnabled(userId)) {
            try {
                if (webPushSender.send(subscription, payload)) {
                    subscriptionRepository.deleteById(subscription.id());
                    log.debug("web push subscription expired, removed: userId={}", userId);
                }
            } catch (RuntimeException e) {
                log.warn("web push failed: userId={}, subscriptionId={}", userId,
                        subscription.id(), e);
            }
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "web push is disabled");
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record WebPushConfigView(boolean enabled, String publicKey) {
    }
}
