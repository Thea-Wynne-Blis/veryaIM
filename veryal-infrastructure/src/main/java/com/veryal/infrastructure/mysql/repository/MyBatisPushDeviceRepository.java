package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.push.PushDevice;
import com.veryal.domain.push.PushDeviceRepository;
import com.veryal.domain.push.PushPlatform;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.PushDeviceMapper;
import com.veryal.infrastructure.mysql.po.PushDevicePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 推送设备 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisPushDeviceRepository implements PushDeviceRepository {

    private final PushDeviceMapper pushDeviceMapper;

    @Override
    public void upsert(PushDevice device) {
        PushDevicePO existing = pushDeviceMapper.selectOne(Wrappers.<PushDevicePO>lambdaQuery()
                .eq(PushDevicePO::getUserId, device.userId())
                .eq(PushDevicePO::getDeviceId, device.deviceId()));
        PushDevicePO po = toPO(device);
        if (existing == null) {
            pushDeviceMapper.insert(po);
        } else {
            po.id = existing.id;
            po.createdAt = existing.createdAt;
            pushDeviceMapper.updateById(po);
        }
    }

    @Override
    public Optional<PushDevice> findByUserAndDevice(long userId, String deviceId) {
        return Optional.ofNullable(pushDeviceMapper.selectOne(Wrappers.<PushDevicePO>lambdaQuery()
                        .eq(PushDevicePO::getUserId, userId)
                        .eq(PushDevicePO::getDeviceId, deviceId)))
                .map(this::toDomain);
    }

    @Override
    public List<PushDevice> listEnabled(long userId) {
        return list(userId, true);
    }

    @Override
    public List<PushDevice> listByUser(long userId) {
        return list(userId, false);
    }

    private List<PushDevice> list(long userId, boolean enabledOnly) {
        var query = Wrappers.<PushDevicePO>lambdaQuery()
                .eq(PushDevicePO::getUserId, userId)
                .orderByDesc(PushDevicePO::getUpdatedAt);
        if (enabledOnly) {
            query.eq(PushDevicePO::getEnabled, 1);
        }
        return pushDeviceMapper.selectList(query).stream().map(this::toDomain).toList();
    }

    @Override
    public void disable(long userId, String deviceId) {
        pushDeviceMapper.update(null, Wrappers.<PushDevicePO>lambdaUpdate()
                .eq(PushDevicePO::getUserId, userId)
                .eq(PushDevicePO::getDeviceId, deviceId)
                .set(PushDevicePO::getEnabled, 0)
                .set(PushDevicePO::getUpdatedAt, java.time.LocalDateTime.now()));
    }

    private PushDevice toDomain(PushDevicePO po) {
        return new PushDevice(po.id, po.userId, po.deviceId,
                PushPlatform.from(po.platform), po.token, po.enabled == 1,
                SqlTime.toInstant(po.createdAt), SqlTime.toInstant(po.updatedAt));
    }

    private PushDevicePO toPO(PushDevice device) {
        PushDevicePO po = new PushDevicePO();
        po.id = device.id();
        po.userId = device.userId();
        po.deviceId = device.deviceId();
        po.platform = device.platform().name();
        po.token = device.token();
        po.enabled = device.enabled() ? 1 : 0;
        po.createdAt = SqlTime.toLocal(device.createdAt());
        po.updatedAt = SqlTime.toLocal(device.updatedAt());
        return po;
    }
}
