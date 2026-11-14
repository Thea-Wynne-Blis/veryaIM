package com.veryal.application.message;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 投递回执跟踪（V0.1 单机内存实现）。
 *
 * <p>完整的多端投递状态将在 V0.3 引入 RabbitMQ + 投递记录表后替换本实现。</p>
 */
@Component
public class DeliveryTracker {

    private static final int MAX_TRACKED_MESSAGES = 100_000;

    private final ConcurrentMap<Long, Set<String>> deliveredDevices = new ConcurrentHashMap<>();

    public void record(long messageId, String deviceId) {
        if (deliveredDevices.size() > MAX_TRACKED_MESSAGES) {
            deliveredDevices.clear();
        }
        deliveredDevices.computeIfAbsent(messageId, id -> ConcurrentHashMap.newKeySet()).add(deviceId);
    }

    public boolean delivered(long messageId, String deviceId) {
        Set<String> devices = deliveredDevices.get(messageId);
        return devices != null && devices.contains(deviceId);
    }

    public int deliveredDeviceCount(long messageId) {
        Set<String> devices = deliveredDevices.get(messageId);
        return devices == null ? 0 : devices.size();
    }
}
