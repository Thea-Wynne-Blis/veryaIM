package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_user_privacy 映射对象。
 */
@Getter
@Setter
@TableName("im_user_privacy")
public class UserPrivacyPO {

    @TableId(value = "user_id", type = IdType.INPUT)
    public Long userId;
    public int searchByPhone;
    public int searchByUsername;
    public int friendPolicy;
    public int showOnline;
    public int readReceipt;
    public int invitedToGroup;
    public LocalDateTime updatedAt;
}
