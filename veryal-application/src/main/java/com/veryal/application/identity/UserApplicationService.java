package com.veryal.application.identity;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.device.Device;
import com.veryal.domain.device.DeviceRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 用户资料应用服务。资料读取走 L1（Caffeine）缓存，更新时失效。
 */
@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final TimeProvider timeProvider;

    @Cacheable(cacheNames = "user", key = "#userId")
    @Transactional(readOnly = true)
    public UserProfile getProfile(long userId) {
        return UserProfile.from(requireUser(userId));
    }

    @CacheEvict(cacheNames = "user", key = "#userId")
    @Transactional
    public UserProfile updateProfile(long userId, String nickname, String avatar) {
        User user = requireUser(userId);
        if (nickname != null && nickname.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "nickname too long");
        }
        if (avatar != null && avatar.length() > 512) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "avatar url too long");
        }
        User updated = user.updateProfile(nickname, avatar, timeProvider.now());
        userRepository.update(updated);
        return UserProfile.from(updated);
    }

    /**
     * 登录设备列表。同一平台下同名的历史记录（例如多次清理浏览器缓存产生的多条
     * Browser）合并为一条，当前登录设备排在最前。
     */
    @Transactional(readOnly = true)
    public List<DeviceInfo> listDevices(long userId, String currentDeviceId) {
        Map<String, List<Device>> groups = new LinkedHashMap<>();
        for (Device device : deviceRepository.findByUserId(userId)) {
            groups.computeIfAbsent(deviceGroupKey(device), key -> new ArrayList<>()).add(device);
        }
        return groups.values().stream()
                .map(group -> toDeviceInfo(group, currentDeviceId))
                .sorted(Comparator.comparing(DeviceInfo::current).reversed()
                        .thenComparing(DeviceInfo::lastLoginAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 移除登录设备：连同该设备同平台同名的历史记录一起清理，当前设备不可移除。
     *
     * @return 被移除的记录条数
     */
    @Transactional
    public int removeDevice(long userId, String currentDeviceId, String deviceId) {
        Device target = deviceRepository.findByDeviceId(deviceId)
                .filter(device -> device.userId() == userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND,
                        "device not found: " + deviceId));
        String groupKey = deviceGroupKey(target);
        List<String> removable = deviceRepository.findByUserId(userId).stream()
                .filter(device -> deviceGroupKey(device).equals(groupKey))
                .map(Device::deviceId)
                .filter(id -> !id.equals(currentDeviceId))
                .toList();
        if (removable.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "cannot remove current device");
        }
        deviceRepository.deleteByUserIdAndDeviceIds(userId, removable);
        return removable.size();
    }

    private static DeviceInfo toDeviceInfo(List<Device> group, String currentDeviceId) {
        Device latest = group.stream()
                .max(Comparator.comparing(Device::lastLoginAt,
                        Comparator.nullsFirst(Comparator.<Instant>naturalOrder())))
                .orElse(group.get(0));
        boolean current = currentDeviceId != null
                && group.stream().anyMatch(device -> device.deviceId().equals(currentDeviceId));
        return DeviceInfo.from(latest, group.size(), current);
    }

    private static String deviceGroupKey(Device device) {
        String name = device.deviceName() == null ? "" : device.deviceName().trim().toLowerCase(Locale.ROOT);
        return device.platform().name() + "|" + name;
    }

    private User requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "user not found: " + userId));
    }
}
