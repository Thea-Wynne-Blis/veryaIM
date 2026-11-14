package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_conversation 映射对象（MyBatis-Plus 实体）。
 */
@Getter
@TableName("im_conversation")
public class ConversationPO {

    @TableId(type = IdType.INPUT)
    public String id;
    public long tenantId;
    public int conversationType;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
