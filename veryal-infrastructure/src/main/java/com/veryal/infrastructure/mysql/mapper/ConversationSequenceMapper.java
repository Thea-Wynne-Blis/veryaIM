package com.veryal.infrastructure.mysql.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 会话序号表 Mapper。行锁分配序号属于数据库特定能力，保留显式 SQL。
 */
public interface ConversationSequenceMapper {

    @Insert("INSERT IGNORE INTO im_conversation_sequence (conversation_id, last_seq) VALUES (#{conversationId}, 0)")
    int insertIgnore(@Param("conversationId") String conversationId);

    @Select("SELECT last_seq FROM im_conversation_sequence WHERE conversation_id = #{conversationId} FOR UPDATE")
    long selectForUpdate(@Param("conversationId") String conversationId);

    @Select("SELECT last_seq FROM im_conversation_sequence WHERE conversation_id = #{conversationId}")
    Long selectCurrent(@Param("conversationId") String conversationId);

    @Update("UPDATE im_conversation_sequence SET last_seq = #{lastSeq} WHERE conversation_id = #{conversationId}")
    int updateSeq(@Param("conversationId") String conversationId, @Param("lastSeq") long lastSeq);
}
