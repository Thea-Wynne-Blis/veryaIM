package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_platform_refresh_token 映射对象。
 */
@Getter
@TableName("im_platform_refresh_token")
public class PlatformRefreshTokenPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long adminId;
    public String tokenHash;
    public LocalDateTime expiresAt;
    public int revoked;
    public LocalDateTime createdAt;
}
