package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_group 映射对象。群 ID 与群会话 ID 相同。
 */
@Getter
@TableName("im_group")
public class GroupPO {

    @TableId(type = IdType.INPUT)
    public String id;
    public long tenantId;
    public String name;
    public long ownerId;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
