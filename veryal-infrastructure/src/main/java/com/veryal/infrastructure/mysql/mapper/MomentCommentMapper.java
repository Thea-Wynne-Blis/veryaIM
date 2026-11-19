package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.MomentCommentPO;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 动态评论 Mapper。每条动态取前 N 条的窗口查询写在 MomentCommentMapper.xml。
 */
public interface MomentCommentMapper extends BaseMapper<MomentCommentPO> {

    List<MomentCommentPO> selectFirstComments(@Param("momentIds") Collection<Long> momentIds,
                                              @Param("perMoment") int perMoment);
}
