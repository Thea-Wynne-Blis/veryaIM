package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_report 映射对象。
 */
@Getter
@Setter
@TableName("im_report")
public class ReportPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long reporterId;
    public String targetType;
    public String targetId;
    public String reason;
    public String detail;
    public int status;
    public Long handledBy;
    public LocalDateTime handledAt;
    public LocalDateTime createdAt;
}
