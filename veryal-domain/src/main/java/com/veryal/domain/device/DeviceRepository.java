package com.veryal.domain.device;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 设备仓储端口。
 */
public interface DeviceRepository {

    Optional<Device> findByDeviceId(String deviceId);

    List<Device> findByUserId(long userId);

    void save(Device device);

    void update(Device device);

    void touchLastLogin(String deviceId, Instant loginAt);

    void delete(String deviceId);
}
