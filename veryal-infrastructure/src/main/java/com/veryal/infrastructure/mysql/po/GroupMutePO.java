package com.veryal.infrastructure.mysql.po;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_group_mute 映射对象。
 */
@Getter
@Setter
public class GroupMutePO {

    public String groupId;
    public long userId;
    public LocalDateTime mutedUntil;
    public LocalDateTime createdAt;
}
