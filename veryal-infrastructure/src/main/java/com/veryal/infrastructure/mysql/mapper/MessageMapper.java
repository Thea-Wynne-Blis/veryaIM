package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.MessagePO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 消息表 Mapper。游标分页与最大序号使用显式 SQL，其余 CRUD 由 MyBatis-Plus 提供。
 */
public interface MessageMapper extends BaseMapper<MessagePO> {

    @Select("""
            SELECT id, tenant_id, conversation_id, sender_id, client_msg_id, seq, msg_type, content, created_at
            FROM im_message
            WHERE tenant_id = #{tenantId}
              AND conversation_id = #{conversationId}
              AND seq > #{afterSeq}
            ORDER BY seq ASC
            LIMIT #{limit}
            """)
    List<MessagePO> selectPageAfter(@Param("tenantId") long tenantId,
                                    @Param("conversationId") String conversationId,
                                    @Param("afterSeq") long afterSeq,
                                    @Param("limit") int limit);

    @Select("""
            SELECT COALESCE(MAX(seq), 0)
            FROM im_message
            WHERE tenant_id = #{tenantId}
              AND conversation_id = #{conversationId}
            """)
    long selectMaxSeq(@Param("tenantId") long tenantId, @Param("conversationId") String conversationId);
}
