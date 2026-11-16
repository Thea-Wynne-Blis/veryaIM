package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.delivery.DeliveryRecord;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 消息设备投递记录响应。
 */
public record DeliveryResponse(
                                 @Schema(description = "设备唯一标识")
                                 String deviceId,
                                 long deliveredSequence,
                                 @Schema(description = "投递时间")
                                 Instant deliveredAt
) {

    public static DeliveryResponse from(DeliveryRecord record) {
        return new DeliveryResponse(record.deviceId(), record.deliveredSequence(), record.deliveredAt());
    }
}
