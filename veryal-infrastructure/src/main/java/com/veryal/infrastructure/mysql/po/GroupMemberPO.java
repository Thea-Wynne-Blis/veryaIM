package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_group_member 映射对象。
 */
@Getter
@TableName("im_group_member")
public class GroupMemberPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public String groupId;
    public long userId;
    public int role;
    public LocalDateTime joinedAt;
}
