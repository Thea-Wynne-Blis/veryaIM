package com.veryal.application.identity;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.VeryalException;
import com.veryal.domain.presence.PresenceRecord;
import com.veryal.domain.presence.PresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public void offline(AuthenticatedUser user, String connectionId) {
        presenceRepository.markOffline(user.userId(), user.deviceId(), connectionId);
    }

    public List<PresenceRecord> onlineDevices(long userId) {
        return presenceRepository.findByUserId(userId);
    }
}
