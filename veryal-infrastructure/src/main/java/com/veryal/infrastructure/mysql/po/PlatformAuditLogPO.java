package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_platform_audit_log 映射对象。
 */
@Getter
@TableName("im_platform_audit_log")
public class PlatformAuditLogPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long adminId;
    public String action;
    public String targetType;
    public String targetId;
    public String detail;
    public LocalDateTime createdAt;
}
