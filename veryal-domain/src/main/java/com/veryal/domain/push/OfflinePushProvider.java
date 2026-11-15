package com.veryal.domain.push;

/**
 * 离线推送提供方：由 APNs/FCM/厂商网关适配器实现。
 */
public interface OfflinePushProvider {

    void push(OfflinePushMessage message);
}
