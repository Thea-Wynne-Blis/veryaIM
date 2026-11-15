package com.veryal.application.push;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.push.PushDevice;
import com.veryal.domain.push.PushDeviceRepository;
import com.veryal.domain.push.PushPlatform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 推送设备 token 管理。
 */
@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private static final int MAX_TOKEN_LENGTH = 512;

    private final PushDeviceRepository pushDeviceRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public PushDeviceView register(long userId, String deviceId, String platform, String token) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid push deviceId");
        }
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid push token");
        }
        PushPlatform pushPlatform;
        try {
            pushPlatform = PushPlatform.from(platform);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid push platform");
        }
        Instant now = timeProvider.now();
        PushDevice existing = pushDeviceRepository.findByUserAndDevice(userId, deviceId)
                .orElse(null);
        PushDevice device = new PushDevice(existing == null ? idGenerator.nextId() : existing.id(),
                userId, deviceId, pushPlatform, token.trim(), true,
                existing == null ? now : existing.createdAt(), now);
        pushDeviceRepository.upsert(device);
        return PushDeviceView.from(device);
    }

    @Transactional
    public void unregister(long userId, String deviceId) {
        pushDeviceRepository.disable(userId, deviceId);
    }

    @Transactional(readOnly = true)
    public List<PushDeviceView> list(long userId) {
        return pushDeviceRepository.listByUser(userId).stream()
                .map(PushDeviceView::from)
                .toList();
    }

    public record PushDeviceView(String deviceId, String platform, String token, boolean enabled,
                                 Instant updatedAt) {

        static PushDeviceView from(PushDevice device) {
            return new PushDeviceView(device.deviceId(), device.platform().name(), device.token(),
                    device.enabled(), device.updatedAt());
        }
    }
}
