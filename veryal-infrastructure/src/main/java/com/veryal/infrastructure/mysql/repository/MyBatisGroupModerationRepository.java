package com.veryal.infrastructure.mysql.repository;

import com.veryal.domain.group.GroupModerationRepository;
import com.veryal.domain.group.GroupSetting;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.GroupModerationMapper;
import com.veryal.infrastructure.mysql.po.GroupMutePO;
import com.veryal.infrastructure.mysql.po.GroupSettingPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 群设置/禁言 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisGroupModerationRepository implements GroupModerationRepository {

    private final GroupModerationMapper moderationMapper;

    @Override
    public Optional<GroupSetting> findSetting(String groupId) {
        GroupSettingPO po = moderationMapper.selectSetting(groupId);
        return Optional.ofNullable(po).map(item -> new GroupSetting(item.groupId, item.announcement, item.muteAll == 1,
                SqlTime.toInstant(item.updatedAt), item.updatedBy));
    }

    @Override
    public void saveSetting(GroupSetting setting) {
        GroupSettingPO po = new GroupSettingPO();
        po.groupId = setting.groupId();
        po.announcement = setting.announcement();
        po.muteAll = setting.muteAll() ? 1 : 0;
        po.updatedAt = SqlTime.toLocal(setting.updatedAt());
        po.updatedBy = setting.updatedBy();
        if (moderationMapper.updateSetting(po) == 0) {
            moderationMapper.insertSetting(po);
        }
    }

    @Override
    public Optional<Instant> findMutedUntil(String groupId, long userId) {
        return Optional.ofNullable(moderationMapper.selectMutedUntil(groupId, userId))
                .map(SqlTime::toInstant);
    }

    @Override
    public void mute(String groupId, long userId, Instant until, Instant now) {
        GroupMutePO po = new GroupMutePO();
        po.groupId = groupId;
        po.userId = userId;
        po.mutedUntil = SqlTime.toLocal(until);
        po.createdAt = SqlTime.toLocal(now);
        if (moderationMapper.updateMute(po) == 0) {
            moderationMapper.insertMute(po);
        }
    }

    @Override
    public void unmute(String groupId, long userId) {
        moderationMapper.deleteMute(groupId, userId);
    }
}
