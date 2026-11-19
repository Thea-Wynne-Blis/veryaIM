package com.veryal.infrastructure.mysql.mapper;

import com.veryal.infrastructure.mysql.po.MomentLikePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 点赞 Mapper。联合主键，插入冲突由仓储层转为幂等结果。
 */
public interface MomentLikeMapper {

    @Insert("INSERT INTO im_moment_like (moment_id, user_id, created_at) "
            + "VALUES (#{momentId}, #{userId}, #{createdAt})")
    int insert(@Param("momentId") long momentId, @Param("userId") long userId,
               @Param("createdAt") LocalDateTime createdAt);

    @Delete("DELETE FROM im_moment_like WHERE moment_id = #{momentId} AND user_id = #{userId}")
    int delete(@Param("momentId") long momentId, @Param("userId") long userId);

    List<Long> selectLikedMomentIds(@Param("userId") long userId,
                                    @Param("momentIds") Collection<Long> momentIds);

    List<MomentLikePO> selectLikers(@Param("momentIds") Collection<Long> momentIds,
                                    @Param("perMoment") int perMoment);
}
