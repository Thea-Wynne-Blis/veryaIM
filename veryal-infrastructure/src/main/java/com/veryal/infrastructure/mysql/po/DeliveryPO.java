package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_message_delivery 映射对象。
 */
@Getter
@TableName("im_message_delivery")
public class DeliveryPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long tenantId;
    public long messageId;
    public String conversationId;
    public long userId;
    public String deviceId;
    public long deliveredSequence;
    public LocalDateTime deliveredAt;
}
