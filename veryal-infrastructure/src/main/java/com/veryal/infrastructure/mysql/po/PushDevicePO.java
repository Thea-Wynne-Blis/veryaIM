package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_push_device 映射对象。
 */
@Getter
@Setter
@TableName("im_push_device")
public class PushDevicePO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long userId;
    public String deviceId;
    public String platform;
    public String token;
    public int enabled;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
