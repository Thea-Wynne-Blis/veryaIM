package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.GroupPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 群 Mapper。
 */
public interface GroupMapper extends BaseMapper<GroupPO> {

    @Insert("""
            INSERT IGNORE INTO im_group (id, tenant_id, name, owner_id, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{name}, #{ownerId}, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(GroupPO group);

    @Select("""
            SELECT g.id, g.tenant_id, g.name, g.owner_id, g.created_at, g.updated_at
            FROM im_group g
                     INNER JOIN im_group_member m ON m.group_id = g.id
            WHERE g.tenant_id = #{tenantId}
              AND m.user_id = #{userId}
            ORDER BY g.updated_at DESC
            LIMIT 200
            """)
    List<GroupPO> selectByUser(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
