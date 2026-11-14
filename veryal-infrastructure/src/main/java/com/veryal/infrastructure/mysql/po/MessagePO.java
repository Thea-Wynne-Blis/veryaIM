package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * im_message 映射对象（MyBatis-Plus 实体）。
 */
@TableName("im_message")
public class MessagePO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long tenantId;
    public String conversationId;
    public long senderId;
    public String clientMsgId;
    public long seq;
    public int msgType;
    public byte[] content;
    public LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public long getTenantId() {
        return tenantId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public long getSenderId() {
        return senderId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public long getSeq() {
        return seq;
    }

    public int getMsgType() {
        return msgType;
    }

    public byte[] getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
