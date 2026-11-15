package com.veryal.application.identity;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.identity.UserMuteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 用户全局禁言。
 */
@Service
@RequiredArgsConstructor
public class UserMuteService {

    private static final long MAX_MUTE_MINUTES = 7 * 24 * 60;

    private final UserMuteRepository userMuteRepository;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public void ensureCanSend(long userId) {
        Instant now = timeProvider.now();
        if (userMuteRepository.mutedUntil(userId).filter(until -> until.isAfter(now)).isPresent()) {
            throw new BusinessException(ErrorCode.USER_MUTED);
        }
    }

    @Transactional(readOnly = true)
    public Instant mutedUntil(long userId) {
        return userMuteRepository.mutedUntil(userId).orElse(null);
    }

    @Transactional
    public Instant mute(long adminId, long userId, int minutes, String reason) {
        if (minutes <= 0 || minutes > MAX_MUTE_MINUTES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "mute minutes must be 1-" + MAX_MUTE_MINUTES);
        }
        Instant now = timeProvider.now();
        Instant until = now.plus(Duration.ofMinutes(minutes));
        userMuteRepository.mute(userId, until, reason, adminId, now);
        return until;
    }

    @Transactional
    public void unmute(long userId) {
        userMuteRepository.unmute(userId);
    }
}
