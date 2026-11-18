package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_user 映射对象（MyBatis-Plus 实体）。
 */
@Getter
@TableName("im_user")
public class UserPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public String username;
    public String nickname;
    public String avatar;
    public String region;
    public String signature;
    public int status;
    public String passwordHash;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
