package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_moment_comment 映射对象。
 */
@Getter
@TableName("im_moment_comment")
public class MomentCommentPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long momentId;
    public long userId;
    public Long replyToId;
    public Long replyToUserId;
    public String content;
    public LocalDateTime createdAt;
    public LocalDateTime deletedAt;
}
