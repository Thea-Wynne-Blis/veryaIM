package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * im_conversation_member 映射对象（MyBatis-Plus 实体）。
 */
@TableName("im_conversation_member")
public class ConversationMemberPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public String conversationId;
    public long userId;
    public long lastReadSeq;
    public long lastSyncSeq;
    public LocalDateTime joinedAt;

    public Long getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public long getUserId() {
        return userId;
    }

    public long getLastReadSeq() {
        return lastReadSeq;
    }

    public long getLastSyncSeq() {
        return lastSyncSeq;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
