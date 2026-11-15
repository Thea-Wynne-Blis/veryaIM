package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_platform_admin 映射对象。
 */
@Getter
@TableName("im_platform_admin")
public class PlatformAdminPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public String username;
    public String displayName;
    public String passwordHash;
    public String role;
    public int status;
    public LocalDateTime createdAt;
    public LocalDateTime lastLoginAt;
}
