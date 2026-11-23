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
    /** 头像文件 ID（im_file.id）；头像本体在文件子系统，这里只存引用。 */
    public Long avatarFileId;
    public String region;
    public String signature;
    public int status;
    public String passwordHash;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
