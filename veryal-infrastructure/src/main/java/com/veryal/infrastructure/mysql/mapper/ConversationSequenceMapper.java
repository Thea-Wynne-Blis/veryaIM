package com.veryal.infrastructure.mysql.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话序号表 Mapper。
 *
 * <p>分配用单条 upsert 完成：首次插入 1，已存在则原子自增；新值通过 MySQL 的
 * {@code LAST_INSERT_ID(expr)} 机制在同一连接上取回。相比
 * {@code INSERT IGNORE + SELECT ... FOR UPDATE + UPDATE} 三条语句，
 * 行锁只在单条语句内持有，避免同一会话的发送被长事务串行化。</p>
 */
public interface ConversationSequenceMapper {

    @Insert("INSERT INTO im_conversation_sequence (conversation_id, last_seq) "
            + "VALUES (#{conversationId}, LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE last_seq = LAST_INSERT_ID(last_seq + 1)")
    int advance(@Param("conversationId") String conversationId);

    /** 必须在 {@link #advance(String)} 之后、同一连接上调用。 */
    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Select("SELECT last_seq FROM im_conversation_sequence WHERE conversation_id = #{conversationId}")
    Long selectCurrent(@Param("conversationId") String conversationId);
}
