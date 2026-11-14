package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * im_user 映射对象（MyBatis-Plus 实体）。
 */
@TableName("im_user")
public class UserPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long tenantId;
    public String username;
    public String nickname;
    public String avatar;
    public int status;
    public String passwordHash;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public long getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public int getStatus() {
        return status;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
