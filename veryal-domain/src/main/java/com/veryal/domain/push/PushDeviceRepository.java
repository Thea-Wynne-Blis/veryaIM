package com.veryal.domain.push;

import java.util.List;
import java.util.Optional;

/**
 * 推送设备仓储。
 */
public interface PushDeviceRepository {

    void upsert(PushDevice device);

    Optional<PushDevice> findByUserAndDevice(long userId, String deviceId);

    List<PushDevice> listEnabled(long userId);

    List<PushDevice> listByUser(long userId);

    void disable(long userId, String deviceId);
}
