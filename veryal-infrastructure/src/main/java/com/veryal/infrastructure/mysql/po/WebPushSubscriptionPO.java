package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_web_push_subscription 映射对象。
 */
@Getter
@Setter
@TableName("im_web_push_subscription")
public class WebPushSubscriptionPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long userId;
    public String endpoint;
    public String endpointHash;
    public String p256dh;
    public String auth;
    public String userAgent;
    public int enabled;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
