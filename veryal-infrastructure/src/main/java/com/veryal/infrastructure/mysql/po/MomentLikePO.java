package com.veryal.infrastructure.mysql.po;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_moment_like 映射对象（联合主键，不走 BaseMapper 的按 ID 操作）。
 */
@Getter
public class MomentLikePO {

    public long momentId;
    public long userId;
    public LocalDateTime createdAt;
}
