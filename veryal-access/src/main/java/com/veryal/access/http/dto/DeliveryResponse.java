package com.veryal.access.http.dto;

import com.veryal.domain.delivery.DeliveryRecord;

import java.time.Instant;

/**
 * 消息设备投递记录响应。
 */
public record DeliveryResponse(String deviceId, long deliveredSequence, Instant deliveredAt) {

    public static DeliveryResponse from(DeliveryRecord record) {
        return new DeliveryResponse(record.deviceId(), record.deliveredSequence(), record.deliveredAt());
    }
}
