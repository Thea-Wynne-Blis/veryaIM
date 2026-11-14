package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_conversation_member 映射对象（MyBatis-Plus 实体）。
 */
@Getter
@TableName("im_conversation_member")
public class ConversationMemberPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public String conversationId;
    public long userId;
    public long lastReadSeq;
    public long lastSyncSeq;
    public LocalDateTime joinedAt;
}
