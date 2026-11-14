package com.veryal.domain.delivery;

import java.util.List;
import java.util.Optional;

/**
 * 投递记录仓储端口。
 */
public interface DeliveryRepository {

    /**
     * 幂等记录投递；同一消息同一设备只保留一条，序号只前进。
     */
    void record(DeliveryRecord record);

    Optional<DeliveryRecord> find(long messageId, String deviceId);

    List<DeliveryRecord> findByMessage(long messageId);

    long countByMessage(long messageId);
}
