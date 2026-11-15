package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.DeliveryPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 投递记录 Mapper。
 */
public interface DeliveryMapper extends BaseMapper<DeliveryPO> {

    @Insert("""
            INSERT IGNORE INTO im_message_delivery
                (id, message_id, conversation_id, user_id, device_id, delivered_sequence, delivered_at)
            VALUES (#{id}, #{messageId}, #{conversationId}, #{userId}, #{deviceId},
                    #{deliveredSequence}, #{deliveredAt})
            """)
    int insertIgnore(DeliveryPO delivery);

    @Update("""
            UPDATE im_message_delivery
            SET delivered_sequence = GREATEST(delivered_sequence, #{deliveredSequence}),
                delivered_at = #{deliveredAt}
            WHERE message_id = #{messageId} AND device_id = #{deviceId}
            """)
    int advance(@Param("messageId") long messageId, @Param("deviceId") String deviceId,
                @Param("deliveredSequence") long deliveredSequence,
                @Param("deliveredAt") LocalDateTime deliveredAt);
}
