package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * im_device 映射对象（MyBatis-Plus 实体）。
 */
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

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPlatform() {
        return platform;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
