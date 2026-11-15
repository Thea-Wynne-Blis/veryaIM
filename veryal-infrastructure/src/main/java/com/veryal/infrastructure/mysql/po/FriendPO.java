package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_friend 映射对象。
 */
@Getter
@TableName("im_friend")
public class FriendPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long userId;
    public long friendId;
    public String remark;
    public LocalDateTime createdAt;
}
