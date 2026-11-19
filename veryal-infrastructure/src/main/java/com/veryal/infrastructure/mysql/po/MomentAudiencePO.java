package com.veryal.infrastructure.mysql.po;

import lombok.Getter;

/**
 * im_moment_audience 映射对象（联合主键）。
 */
@Getter
public class MomentAudiencePO {

    public long momentId;
    public long userId;
    public int mode;
}
