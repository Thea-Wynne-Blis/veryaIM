package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.MomentNoticePO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 互动通知 Mapper。
 */
public interface MomentNoticeMapper extends BaseMapper<MomentNoticePO> {

    @Update("UPDATE im_moment_notice SET read_at = #{readAt} "
            + "WHERE user_id = #{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") long userId, @Param("readAt") LocalDateTime readAt);
}
