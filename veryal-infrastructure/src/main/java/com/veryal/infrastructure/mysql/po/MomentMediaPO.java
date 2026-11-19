package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

/**
 * im_moment_media 映射对象。
 */
@Getter
@TableName("im_moment_media")
public class MomentMediaPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long momentId;
    public long fileId;
    public int sortOrder;
    public int kind;
    public Integer width;
    public Integer height;
    public Integer durationSeconds;
}
