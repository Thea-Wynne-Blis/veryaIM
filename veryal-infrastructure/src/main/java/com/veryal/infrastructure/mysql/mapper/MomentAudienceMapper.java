package com.veryal.infrastructure.mysql.mapper;

import com.veryal.infrastructure.mysql.po.MomentAudiencePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 可见名单 Mapper。联合主键，写入时一条动态整体替换。
 */
public interface MomentAudienceMapper {

    @Insert("INSERT INTO im_moment_audience (moment_id, user_id, mode) "
            + "VALUES (#{momentId}, #{userId}, #{mode})")
    int insert(@Param("momentId") long momentId, @Param("userId") long userId,
               @Param("mode") int mode);

    @Select("SELECT moment_id, user_id, mode FROM im_moment_audience "
            + "WHERE moment_id = #{momentId} AND mode = #{mode}")
    List<MomentAudiencePO> selectByMode(@Param("momentId") long momentId,
                                        @Param("mode") int mode);
}
