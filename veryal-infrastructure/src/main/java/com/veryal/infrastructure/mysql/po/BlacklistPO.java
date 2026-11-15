package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_blacklist 映射对象。
 */
@Getter
@TableName("im_blacklist")
public class BlacklistPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long userId;
    public long blockedUserId;
    public LocalDateTime createdAt;
}
