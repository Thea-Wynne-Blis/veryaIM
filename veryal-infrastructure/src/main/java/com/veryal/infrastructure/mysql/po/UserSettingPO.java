package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_user_setting 映射对象：目前只承载聊天背景，后续用户级偏好可以继续加列。
 */
@Getter
@Setter
@TableName("im_user_setting")
public class UserSettingPO {

    @TableId(value = "user_id", type = IdType.INPUT)
    public Long userId;
    public Long chatBgFileId;
    public String chatBgPreset;
    public LocalDateTime updatedAt;
}
