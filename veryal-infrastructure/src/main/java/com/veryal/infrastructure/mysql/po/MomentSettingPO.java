package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_moment_setting 映射对象。user_id 即主键。
 */
@Getter
@TableName("im_moment_setting")
public class MomentSettingPO {

    @TableId(type = IdType.INPUT)
    public Long userId;
    public int visibleDays;
    public long lastReadMomentId;
    public LocalDateTime updatedAt;
}
