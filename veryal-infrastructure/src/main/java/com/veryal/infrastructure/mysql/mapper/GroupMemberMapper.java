package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.GroupMemberPO;
import org.apache.ibatis.annotations.Insert;

/**
 * 群成员 Mapper。加入群使用 INSERT IGNORE 保证幂等。
 */
public interface GroupMemberMapper extends BaseMapper<GroupMemberPO> {

    @Insert("""
            INSERT IGNORE INTO im_group_member (id, group_id, user_id, role, joined_at)
            VALUES (#{id}, #{groupId}, #{userId}, #{role}, #{joinedAt})
            """)
    int insertIgnore(GroupMemberPO member);
}
