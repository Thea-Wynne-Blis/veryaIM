package com.veryal.domain.push;

import java.util.List;
import java.util.Optional;

/**
 * Web Push 订阅仓储。
 */
public interface WebPushSubscriptionRepository {

    void upsert(WebPushSubscription subscription);

    Optional<WebPushSubscription> findByEndpointHash(String endpointHash);

    List<WebPushSubscription> listEnabled(long userId);

    void delete(String endpointHash);

    void deleteById(long id);
}
