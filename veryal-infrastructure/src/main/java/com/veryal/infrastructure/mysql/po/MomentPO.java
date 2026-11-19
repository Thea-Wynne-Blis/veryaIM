package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_moment 映射对象。
 */
@Getter
@TableName("im_moment")
public class MomentPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long authorId;
    public String content;
    public int visibility;
    public int likeCount;
    public int commentCount;
    public int status;
    public LocalDateTime createdAt;
    public LocalDateTime deletedAt;
}
