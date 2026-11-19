package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.MomentPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态 Mapper。信息流的多表过滤写在 MomentMapper.xml。
 */
public interface MomentMapper extends BaseMapper<MomentPO> {

    List<MomentPO> selectFeed(@Param("viewerId") long viewerId,
                              @Param("authorId") Long authorId,
                              @Param("cursor") long cursor,
                              @Param("limit") int limit,
                              @Param("now") LocalDateTime now);

    @Update("UPDATE im_moment SET like_count = GREATEST(0, like_count + #{delta}) "
            + "WHERE id = #{momentId}")
    int addLikeCount(@Param("momentId") long momentId, @Param("delta") int delta);

    @Update("UPDATE im_moment SET comment_count = GREATEST(0, comment_count + #{delta}) "
            + "WHERE id = #{momentId}")
    int addCommentCount(@Param("momentId") long momentId, @Param("delta") int delta);

    @Update("UPDATE im_moment SET status = #{status} WHERE id = #{momentId}")
    int updateStatus(@Param("momentId") long momentId, @Param("status") int status);
}
