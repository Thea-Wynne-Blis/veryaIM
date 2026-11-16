package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.ConversationPO;
import com.veryal.infrastructure.mysql.po.ConversationSendContextPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话 Mapper。幂等插入与联表查询使用显式 SQL，其余 CRUD 由 MyBatis-Plus 提供。
 */
public interface ConversationMapper extends BaseMapper<ConversationPO> {

    @Insert("""
            INSERT IGNORE INTO im_conversation (id, conversation_type, created_at, updated_at)
            VALUES (#{id}, #{conversationType}, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(ConversationPO conversation);

    @Select("""
            SELECT c.id, c.conversation_type, c.created_at, c.updated_at
            FROM im_conversation c
                     INNER JOIN im_conversation_member m ON m.conversation_id = c.id
            WHERE m.user_id = #{userId}
            ORDER BY c.updated_at DESC
            LIMIT 200
            """)
    List<ConversationPO> selectByUser(@Param("userId") long userId);

    @Select("""
            SELECT c.conversation_type AS conversationType,
                   (SELECT m.user_id
                      FROM im_conversation_member m
                     WHERE m.conversation_id = c.id
                       AND m.user_id = #{userId})                    AS memberUserId,
                   (SELECT um.muted_until
                      FROM im_user_mute um
                     WHERE um.user_id = #{userId})                   AS mutedUntil,
                   CASE WHEN c.conversation_type = 1 THEN
                        (SELECT m2.user_id
                           FROM im_conversation_member m2
                          WHERE m2.conversation_id = c.id
                            AND m2.user_id <> #{userId}
                          LIMIT 1)
                   END                                               AS peerUserId,
                   CASE WHEN c.conversation_type = 1 THEN
                        EXISTS(SELECT 1
                                 FROM im_blacklist b
                                WHERE b.user_id = (SELECT m3.user_id
                                                     FROM im_conversation_member m3
                                                    WHERE m3.conversation_id = c.id
                                                      AND m3.user_id <> #{userId}
                                                    LIMIT 1)
                                  AND b.blocked_user_id = #{userId})
                   END                                               AS peerBlocked
            FROM im_conversation c
            WHERE c.id = #{conversationId}
            """)
    ConversationSendContextPO selectSendContext(@Param("conversationId") String conversationId,
                                                @Param("userId") long userId);

    @Select("""
            SELECT user_id
            FROM im_conversation_member
            WHERE conversation_id = #{conversationId}
              AND user_id > #{afterUserId}
            ORDER BY user_id ASC
            LIMIT #{limit}
            """)
    List<Long> selectMemberIdsPage(@Param("conversationId") String conversationId,
                                   @Param("afterUserId") long afterUserId,
                                   @Param("limit") int limit);
}
