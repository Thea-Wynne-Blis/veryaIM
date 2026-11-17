package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.ConversationCardRowPO;
import com.veryal.infrastructure.mysql.po.ConversationMemberPrefsPO;
import com.veryal.infrastructure.mysql.po.ConversationPO;
import com.veryal.infrastructure.mysql.po.ConversationSendContextPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
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
            SET last_read_seq = GREATEST(last_read_seq, #{sequence}),
                manual_unread = 0
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            """)
    int advanceReadCursor(@Param("conversationId") String conversationId,
                          @Param("userId") long userId,
                          @Param("sequence") long sequence);

    /**
     * 置顶 / 免打扰：单条 UPDATE，{@code null} 表示该项不改。
     */
    @Update("""
            <script>
            UPDATE im_conversation_member
            <set>
                <if test="pinned != null">pinned = #{pinned},</if>
                <if test="muted != null">muted = #{muted},</if>
            </set>
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            </script>
            """)
    int updateMemberFlags(@Param("conversationId") String conversationId,
                          @Param("userId") long userId,
                          @Param("pinned") Boolean pinned,
                          @Param("muted") Boolean muted);

    /**
     * 不显示：写入设置时间；取消时写 NULL。{@code Param} 为 {@code hidden_at}。
     */
    @Update("""
            UPDATE im_conversation_member
            SET hidden_at = #{hiddenAt}
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            """)
    int updateMemberHidden(@Param("conversationId") String conversationId,
                           @Param("userId") long userId,
                           @Param("hiddenAt") LocalDateTime hiddenAt);

    /**
     * 清空本人记录：抬高可见水位、取消置顶与手动未读、标为不显示。
     * 水位只前进，避免并发重放把已清空的记录又放出来。
     */
    @Update("""
            UPDATE im_conversation_member
            SET cleared_seq = GREATEST(cleared_seq, #{sequence}),
                last_read_seq = GREATEST(last_read_seq, #{sequence}),
                manual_unread = 0,
                pinned = 0,
                hidden_at = #{now}
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            """)
    int clearMemberHistory(@Param("conversationId") String conversationId,
                           @Param("userId") long userId,
                           @Param("sequence") long sequence,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE im_conversation_member
            SET manual_unread = #{manualUnread}
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            """)
    int updateMemberManualUnread(@Param("conversationId") String conversationId,
                                 @Param("userId") long userId,
                                 @Param("manualUnread") boolean manualUnread);


    /** 我参与的会话（按会话更新时间倒序）。 */
    List<ConversationPO> selectByUser(@Param("userId") long userId);

    /** 成员级会话设置（置顶 / 免打扰 / 不显示 / 清空水位 / 手动未读）。 */
    @Select("""
            SELECT pinned, muted, hidden_at, cleared_seq, manual_unread
            FROM im_conversation_member
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            """)
    ConversationMemberPrefsPO selectMemberPrefs(@Param("conversationId") String conversationId,
                                                @Param("userId") long userId);

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
