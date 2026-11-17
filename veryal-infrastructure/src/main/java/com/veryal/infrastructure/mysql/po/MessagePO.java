package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_message 映射对象（MyBatis-Plus 实体）。
 */
@Getter
@TableName("im_message")
public class MessagePO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public String conversationId;
    public long senderId;
    public String clientMsgId;
    public long seq;
    public int msgType;
    public byte[] content;
    public LocalDateTime createdAt;
    public LocalDateTime recalledAt;
    public Long recalledBy;
    public Long replyToId;
    public Long replySenderId;
    public String replyPreview;
}
