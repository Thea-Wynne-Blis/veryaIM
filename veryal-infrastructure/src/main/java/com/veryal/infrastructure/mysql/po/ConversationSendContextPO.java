package com.veryal.infrastructure.mysql.po;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 发送上下文查询结果，对应 {@code ConversationMapper.selectSendContext} 的部分字段投影。
 */
@Getter
@Setter
public class ConversationSendContextPO {

    public int conversationType;
    public Long memberUserId;
    public LocalDateTime mutedUntil;
    public Long peerUserId;
    public Boolean peerBlocked;
}
