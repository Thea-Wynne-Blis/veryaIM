package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.ConversationCardRowPO;
import com.veryal.infrastructure.mysql.po.ConversationPO;
import com.veryal.infrastructure.mysql.po.ConversationSendContextPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 会话 Mapper。幂等插入与读游标推进使用显式 SQL，其余 CRUD 由 MyBatis-Plus 提供。
 *
 * <p>多表 / 子查询类查询的 SQL 写在 {@code resources/mapper/ConversationMapper.xml}，
 * 这里只保留方法声明。</p>
 */
public interface ConversationMapper extends BaseMapper<ConversationPO> {

    @Insert("""
            INSERT IGNORE INTO im_conversation (id, conversation_type, created_at, updated_at)
            VALUES (#{id}, #{conversationType}, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(ConversationPO conversation);

    @Update("""
            UPDATE im_conversation_member
            SET last_read_seq = GREATEST(last_read_seq, #{sequence})
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            """)
    int advanceReadCursor(@Param("conversationId") String conversationId,
                          @Param("userId") long userId,
                          @Param("sequence") long sequence);

    /** 我参与的会话（按会话更新时间倒序）。 */
    List<ConversationPO> selectByUser(@Param("userId") long userId);

    /** 发送前的鉴权上下文：成员关系、全局禁言、单聊对端与拉黑状态。 */
    ConversationSendContextPO selectSendContext(@Param("conversationId") String conversationId,
                                                @Param("userId") long userId);

    /** 会话卡片列表：一条联表查询取回全部展示字段。 */
    List<ConversationCardRowPO> selectCardRows(@Param("userId") long userId);

    /** 成员 id 游标分页（大群拉人/推送用）。 */
    List<Long> selectMemberIdsPage(@Param("conversationId") String conversationId,
                                   @Param("afterUserId") long afterUserId,
                                   @Param("limit") int limit);
}
