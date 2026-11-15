package com.veryal.infrastructure.mysql.mapper;

import com.veryal.infrastructure.mysql.po.GroupMutePO;
import com.veryal.infrastructure.mysql.po.GroupSettingPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 群设置/禁言 Mapper。
 */
public interface GroupModerationMapper {

    @Select("SELECT group_id, announcement, mute_all, updated_at, updated_by "
            + "FROM im_group_setting WHERE group_id = #{groupId}")
    GroupSettingPO selectSetting(@Param("groupId") String groupId);

    @Insert("INSERT INTO im_group_setting "
            + "(group_id, announcement, mute_all, updated_at, updated_by) "
            + "VALUES (#{groupId}, #{announcement}, #{muteAll}, #{updatedAt}, #{updatedBy})")
    int insertSetting(GroupSettingPO po);

    @Update("UPDATE im_group_setting SET announcement = #{announcement}, mute_all = #{muteAll}, "
            + "updated_at = #{updatedAt}, updated_by = #{updatedBy} WHERE group_id = #{groupId}")
    int updateSetting(GroupSettingPO po);

    @Select("SELECT muted_until FROM im_group_mute WHERE group_id = #{groupId} AND user_id = #{userId}")
    LocalDateTime selectMutedUntil(@Param("groupId") String groupId, @Param("userId") long userId);

    @Insert("INSERT INTO im_group_mute (group_id, user_id, muted_until, created_at) "
            + "VALUES (#{groupId}, #{userId}, #{mutedUntil}, #{createdAt})")
    int insertMute(GroupMutePO po);

    @Update("UPDATE im_group_mute SET muted_until = #{mutedUntil} "
            + "WHERE group_id = #{groupId} AND user_id = #{userId}")
    int updateMute(GroupMutePO po);

    @Delete("DELETE FROM im_group_mute WHERE group_id = #{groupId} AND user_id = #{userId}")
    int deleteMute(@Param("groupId") String groupId, @Param("userId") long userId);
}
