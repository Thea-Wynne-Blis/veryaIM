package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_moment_notice 映射对象。
 */
@Getter
@TableName("im_moment_notice")
public class MomentNoticePO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long userId;
    public long actorId;
    public int type;
    public long momentId;
    public Long commentId;
    public LocalDateTime readAt;
    public LocalDateTime createdAt;
}
