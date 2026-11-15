package com.veryal.infrastructure.mysql.po;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_group_setting 映射对象。
 */
@Getter
@Setter
public class GroupSettingPO {

    public String groupId;
    public String announcement;
    public int muteAll;
    public LocalDateTime updatedAt;
    public long updatedBy;
}
