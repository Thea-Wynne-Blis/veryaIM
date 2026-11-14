package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.device.Device;
import com.veryal.domain.device.DeviceRepository;
import com.veryal.domain.device.Platform;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.DeviceMapper;
import com.veryal.infrastructure.mysql.po.DevicePO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 设备仓储 MyBatis-Plus 实现。
 */
@Repository
public class MyBatisDeviceRepository implements DeviceRepository {

    private final DeviceMapper deviceMapper;

    public MyBatisDeviceRepository(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    @Override
    public Optional<Device> findByDeviceId(String deviceId) {
        return Optional.ofNullable(deviceMapper.selectOne(Wrappers.<DevicePO>lambdaQuery()
                        .eq(DevicePO::getDeviceId, deviceId)))
                .map(this::toDomain);
    }

    @Override
    public List<Device> findByUserId(long userId) {
        return deviceMapper.selectList(Wrappers.<DevicePO>lambdaQuery()
                        .eq(DevicePO::getUserId, userId)
                        .orderByDesc(DevicePO::getLastLoginAt))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void save(Device device) {
        deviceMapper.insert(toPO(device));
    }

    @Override
    public void update(Device device) {
        deviceMapper.updateById(toPO(device));
    }

    @Override
    public void touchLastLogin(String deviceId, Instant loginAt) {
        deviceMapper.update(null, Wrappers.<DevicePO>lambdaUpdate()
                .eq(DevicePO::getDeviceId, deviceId)
                .set(DevicePO::getLastLoginAt, SqlTime.toLocal(loginAt)));
    }

    @Override
    public void delete(String deviceId) {
        deviceMapper.delete(Wrappers.<DevicePO>lambdaQuery()
                .eq(DevicePO::getDeviceId, deviceId));
    }

    private Device toDomain(DevicePO po) {
        return new Device(po.id, po.userId, po.deviceId, Platform.from(po.platform), po.deviceName,
                SqlTime.toInstant(po.lastLoginAt), SqlTime.toInstant(po.createdAt));
    }

    private DevicePO toPO(Device device) {
        DevicePO po = new DevicePO();
        po.id = device.id();
        po.userId = device.userId();
        po.deviceId = device.deviceId();
        po.platform = device.platform().name();
        po.deviceName = device.deviceName();
        po.lastLoginAt = SqlTime.toLocal(device.lastLoginAt());
        po.createdAt = SqlTime.toLocal(device.createdAt());
        return po;
    }
}
