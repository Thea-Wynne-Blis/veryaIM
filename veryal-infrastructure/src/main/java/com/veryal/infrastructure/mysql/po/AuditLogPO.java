package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_audit_log 映射对象。
 */
@Getter
@TableName("im_audit_log")
public class AuditLogPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long operatorId;
    public String action;
    public String targetType;
    public String targetId;
    public String detail;
    public LocalDateTime createdAt;
}
