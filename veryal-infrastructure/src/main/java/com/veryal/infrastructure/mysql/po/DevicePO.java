package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_device 映射对象（MyBatis-Plus 实体）。
 */
@Getter
@TableName("im_device")
public class DevicePO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long userId;
    public String deviceId;
    public String platform;
    public String deviceName;
    public LocalDateTime lastLoginAt;
    public LocalDateTime createdAt;
}
