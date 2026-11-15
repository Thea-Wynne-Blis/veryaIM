package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_user_mute 映射对象。
 */
@Getter
@Setter
@TableName("im_user_mute")
public class UserMutePO {

    @TableId(value = "user_id", type = IdType.INPUT)
    public Long userId;
    public LocalDateTime mutedUntil;
    public String reason;
    public long updatedBy;
    public LocalDateTime updatedAt;
}
