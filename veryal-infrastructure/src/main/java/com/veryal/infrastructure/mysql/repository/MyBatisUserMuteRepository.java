package com.veryal.infrastructure.mysql.repository;

import com.veryal.domain.identity.UserMuteRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.UserMuteMapper;
import com.veryal.infrastructure.mysql.po.UserMutePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 用户禁言 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisUserMuteRepository implements UserMuteRepository {

    private final UserMuteMapper muteMapper;

    @Override
    public Optional<Instant> mutedUntil(long userId) {
        return Optional.ofNullable(muteMapper.selectById(userId))
                .map(po -> SqlTime.toInstant(po.mutedUntil));
    }

    @Override
    public void mute(long userId, Instant until, String reason, long operatorId, Instant now) {
        UserMutePO po = new UserMutePO();
        po.userId = userId;
        po.mutedUntil = SqlTime.toLocal(until);
        po.reason = reason;
        po.updatedBy = operatorId;
        po.updatedAt = SqlTime.toLocal(now);
        if (muteMapper.selectById(userId) == null) {
            muteMapper.insert(po);
        } else {
            muteMapper.updateById(po);
        }
    }

    @Override
    public void unmute(long userId) {
        muteMapper.deleteById(userId);
    }
}
