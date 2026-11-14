package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_outbox 映射对象。主键由 MyBatis-Plus 雪花算法生成。
 */
@Getter
@TableName("im_outbox")
public class OutboxPO {

    @TableId(type = IdType.ASSIGN_ID)
    public Long id;
    public String eventId;
    public String aggregateId;
    public String eventType;
    public byte[] payload;
    public int status;
    public int retryCount;
    public LocalDateTime nextRetryAt;
    public String lastError;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime publishedAt;
}
