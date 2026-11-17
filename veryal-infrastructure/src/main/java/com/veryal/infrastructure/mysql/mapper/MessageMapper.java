package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.MessagePO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 消息表 Mapper。游标分页与全文搜索的 SQL 写在
 * {@code resources/mapper/MessageMapper.xml}，其余 CRUD 由 MyBatis-Plus 提供。
 */
public interface MessageMapper extends BaseMapper<MessagePO> {

    /** 按 seq 游标向后翻页。 */
    List<MessagePO> selectPageAfter(@Param("conversationId") String conversationId,
                                    @Param("afterSeq") long afterSeq,
                                    @Param("limit") int limit);

    /** 会话内最大序号，没有消息时返回 0。 */
    long selectMaxSeq(@Param("conversationId") String conversationId);

    /** 会话内按关键字搜索（只搜文本/表情，跳过已撤回）。 */
    List<MessagePO> searchText(@Param("conversationId") String conversationId,
                               @Param("pattern") String pattern,
                               @Param("afterSeq") long afterSeq,
                               @Param("beforeSeq") long beforeSeq,
                               @Param("limit") int limit);

    @Select("SELECT COUNT(1) FROM im_message")
    long selectCountAll();
}
