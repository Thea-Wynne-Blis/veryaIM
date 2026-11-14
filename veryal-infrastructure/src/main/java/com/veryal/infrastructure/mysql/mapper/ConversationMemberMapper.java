package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.ConversationMemberPO;
import org.apache.ibatis.annotations.Insert;

/**
 * 会话成员 Mapper。成员加入使用 INSERT IGNORE 保证幂等。
 */
public interface ConversationMemberMapper extends BaseMapper<ConversationMemberPO> {

    @Insert("""
            INSERT IGNORE INTO im_conversation_member
                (id, conversation_id, user_id, last_read_seq, last_sync_seq, joined_at)
            VALUES (#{id}, #{conversationId}, #{userId}, #{lastReadSeq}, #{lastSyncSeq}, #{joinedAt})
            """)
    int insertIgnore(ConversationMemberPO member);
}
