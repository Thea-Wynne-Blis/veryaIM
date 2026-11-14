package com.veryal.application.identity;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.device.DeviceRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户资料应用服务。资料读取走 L1（Caffeine）缓存，更新时失效。
 */
@Service
public class UserApplicationService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final TimeProvider timeProvider;

    public UserApplicationService(UserRepository userRepository, DeviceRepository deviceRepository,
                                  TimeProvider timeProvider) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.timeProvider = timeProvider;
    }

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

    @Transactional(readOnly = true)
    public List<DeviceInfo> listDevices(long userId) {
        return deviceRepository.findByUserId(userId).stream().map(DeviceInfo::from).toList();
    }

    private User requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "user not found: " + userId));
    }
}
