package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.GroupMemberPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 群成员 Mapper。加入群使用 INSERT IGNORE 保证幂等。
 */
public interface GroupMemberMapper extends BaseMapper<GroupMemberPO> {

    @Insert("""
            INSERT IGNORE INTO im_group_member (id, group_id, user_id, role, joined_at)
            VALUES (#{id}, #{groupId}, #{userId}, #{role}, #{joinedAt})
            """)
    int insertIgnore(GroupMemberPO member);

    /**
     * 一次查询取回多个群的成员数，避免群列表按群逐个 count。
     */
    @Select("""
            <script>
            SELECT group_id AS groupId, COUNT(*) AS total
            FROM im_group_member
            WHERE group_id IN
            <foreach collection="groupIds" item="gid" open="(" separator="," close=")">#{gid}</foreach>
            GROUP BY group_id
            </script>
            """)
    List<Map<String, Object>> countByGroupIds(@Param("groupIds") Collection<String> groupIds);
}
