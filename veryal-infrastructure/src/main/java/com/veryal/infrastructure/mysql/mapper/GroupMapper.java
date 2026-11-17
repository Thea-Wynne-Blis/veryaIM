package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.GroupPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 群 Mapper。联表查询的 SQL 写在 {@code resources/mapper/GroupMapper.xml}。
 */
public interface GroupMapper extends BaseMapper<GroupPO> {

    @Insert("""
            INSERT IGNORE INTO im_group (id, name, owner_id, created_at, updated_at)
            VALUES (#{id}, #{name}, #{ownerId}, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(GroupPO group);

    /** 我加入的群（按群更新时间倒序）。 */
    List<GroupPO> selectByUser(@Param("userId") long userId);
}
