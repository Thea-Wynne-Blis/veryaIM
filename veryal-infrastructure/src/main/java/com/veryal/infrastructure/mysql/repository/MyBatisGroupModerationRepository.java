package com.veryal.infrastructure.mysql.repository;

import com.veryal.domain.group.GroupModerationRepository;
import com.veryal.domain.group.GroupMute;
import com.veryal.domain.group.GroupSetting;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.GroupModerationMapper;
import com.veryal.infrastructure.mysql.po.GroupMutePO;
import com.veryal.infrastructure.mysql.po.GroupSettingPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 群公告/禁言 MyBatis 实现。
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
    public Optional<GroupMute> findMute(String groupId, long userId) {
        return Optional.ofNullable(moderationMapper.selectMute(groupId, userId)).map(this::toDomain);
    }

    @Override
    public List<GroupMute> findMutes(String groupId) {
        return moderationMapper.selectMutes(groupId).stream().map(this::toDomain).toList();
    }

    @Override
    public void mute(String groupId, long userId, Instant mutedUntil, Instant now) {
        GroupMutePO po = new GroupMutePO();
        po.groupId = groupId;
        po.userId = userId;
        po.mutedUntil = mutedUntil == null ? null : SqlTime.toLocal(mutedUntil);
        po.createdAt = SqlTime.toLocal(now);
        if (moderationMapper.updateMute(po) == 0) {
            moderationMapper.insertMute(po);
        }
    }

    @Override
    public void unmute(String groupId, long userId) {
        moderationMapper.deleteMute(groupId, userId);
    }

    @Override
    public void deleteGroupState(String groupId) {
        moderationMapper.deleteMutes(groupId);
        moderationMapper.deleteSetting(groupId);
    }

    private GroupMute toDomain(GroupMutePO po) {
        return new GroupMute(po.groupId, po.userId,
                po.mutedUntil == null ? null : SqlTime.toInstant(po.mutedUntil));
    }
}
