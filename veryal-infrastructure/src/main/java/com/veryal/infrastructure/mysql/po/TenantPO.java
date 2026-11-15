package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_tenant 映射对象。
 */
@Getter
@TableName("im_tenant")
public class TenantPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public String code;
    public String name;
    public int status;
    public int maxUsers;
    public int maxGroups;
    public long maxStorageBytes;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
