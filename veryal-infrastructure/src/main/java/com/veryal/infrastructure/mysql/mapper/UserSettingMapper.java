package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.UserSettingPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 用户级设置 Mapper。
 *
 * <p>聊天背景的两列都允许为 NULL（清除背景），MyBatis-Plus 的 {@code updateById}
 * 默认会跳过 null 字段，所以这里用显式的 upsert SQL。</p>
 */
public interface UserSettingMapper extends BaseMapper<UserSettingPO> {

    @Insert("""
            INSERT INTO im_user_setting (user_id, chat_bg_file_id, chat_bg_preset, updated_at)
            VALUES (#{userId}, #{fileId}, #{preset}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
                chat_bg_file_id = VALUES(chat_bg_file_id),
                chat_bg_preset = VALUES(chat_bg_preset),
                updated_at = VALUES(updated_at)
            """)
    int upsertBackground(@Param("userId") long userId,
                         @Param("fileId") Long fileId,
                         @Param("preset") String preset,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
