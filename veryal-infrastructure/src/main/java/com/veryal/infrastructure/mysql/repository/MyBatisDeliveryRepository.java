package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.common.core.TenantIds;
import com.veryal.domain.delivery.DeliveryRecord;
import com.veryal.domain.delivery.DeliveryRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.DeliveryMapper;
import com.veryal.infrastructure.mysql.po.DeliveryPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 投递记录 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisDeliveryRepository implements DeliveryRepository {

    private final DeliveryMapper deliveryMapper;

    @Override
    public void record(DeliveryRecord record) {
        DeliveryPO po = toPO(record);
        if (deliveryMapper.insertIgnore(po) > 0) {
            return;
        }
        deliveryMapper.advance(record.messageId(), record.deviceId(), record.deliveredSequence(),
                SqlTime.toLocal(record.deliveredAt()));
    }

    @Override
    public Optional<DeliveryRecord> find(long messageId, String deviceId) {
        return Optional.ofNullable(deliveryMapper.selectOne(Wrappers.<DeliveryPO>lambdaQuery()
                        .eq(DeliveryPO::getMessageId, messageId)
                        .eq(DeliveryPO::getDeviceId, deviceId)))
                .map(this::toDomain);
    }

    @Override
    public List<DeliveryRecord> findByMessage(long messageId) {
        return deliveryMapper.selectList(Wrappers.<DeliveryPO>lambdaQuery()
                        .eq(DeliveryPO::getMessageId, messageId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByMessage(long messageId) {
        Long count = deliveryMapper.selectCount(Wrappers.<DeliveryPO>lambdaQuery()
                .eq(DeliveryPO::getMessageId, messageId));
        return count == null ? 0L : count;
    }

    private DeliveryRecord toDomain(DeliveryPO po) {
        return new DeliveryRecord(po.id, TenantIds.DEFAULT_TENANT_ID, po.messageId,
                po.conversationId, po.userId, po.deviceId, po.deliveredSequence,
                SqlTime.toInstant(po.deliveredAt));
    }

    private DeliveryPO toPO(DeliveryRecord record) {
        DeliveryPO po = new DeliveryPO();
        po.id = record.id();
        po.messageId = record.messageId();
        po.conversationId = record.conversationId();
        po.userId = record.userId();
        po.deviceId = record.deviceId();
        po.deliveredSequence = record.deliveredSequence();
        po.deliveredAt = SqlTime.toLocal(record.deliveredAt());
        return po;
    }
}
