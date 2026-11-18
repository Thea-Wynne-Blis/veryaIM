package com.veryal.application.identity;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.VeryalException;
import com.veryal.domain.presence.PresenceRecord;
import com.veryal.domain.presence.PresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 会话在线状态应用服务。接入层登录 / 断开时调用。
 */
@Service
@RequiredArgsConstructor
public class SessionApplicationService {

    private final PresenceRepository presenceRepository;
    private final TimeProvider timeProvider;

    public void online(AuthenticatedUser user, String gatewayId, String connectionId) {
        if (gatewayId == null || gatewayId.isBlank()) {
            throw new VeryalException(ErrorCode.CONFIGURATION_ERROR, "gatewayId must not be blank");
        }
        presenceRepository.markOnline(PresenceRecord.online(user.userId(), user.deviceId(),
                gatewayId, connectionId, timeProvider.now()));
    }

    public void heartbeat(AuthenticatedUser user, String connectionId) {
        presenceRepository.heartbeat(user.userId(), user.deviceId(), connectionId, timeProvider.now());
    }

    /**
     * 续期在线状态；记录已经因为 TTL 过期被清掉时用当前连接重建。
     *
     * <p>只续期、不重建的话，一旦某次续期晚于 TTL，用户会一直连着却永久显示离线，
     * 直到他重新建连为止。</p>
     */
    public boolean renew(AuthenticatedUser user, String gatewayId, String connectionId) {
        if (presenceRepository.heartbeat(user.userId(), user.deviceId(), connectionId,
                timeProvider.now())) {
            return true;
        }
        boolean superseded = presenceRepository.findByUserId(user.userId()).stream()
                .anyMatch(record -> user.deviceId().equals(record.deviceId()));
        if (superseded) {
            // 同一设备已有更新的连接，旧连接不能把状态抢回来。
            return false;
        }
        presenceRepository.markOnline(PresenceRecord.online(user.userId(), user.deviceId(),
                gatewayId, connectionId, timeProvider.now()));
        return true;
    }

    public void offline(AuthenticatedUser user, String connectionId) {
        presenceRepository.markOffline(user.userId(), user.deviceId(), connectionId);
    }
}
