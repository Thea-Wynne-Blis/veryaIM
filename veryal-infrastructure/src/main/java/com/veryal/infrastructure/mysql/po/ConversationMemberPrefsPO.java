package com.veryal.infrastructure.mysql.po;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 成员级会话设置的读投影，对应 {@code ConversationMapper.selectMemberPrefs}。
 */
@Getter
@Setter
public class ConversationMemberPrefsPO {

    public boolean pinned;
    public boolean muted;
    public LocalDateTime hiddenAt;
    public long clearedSeq;
    public boolean manualUnread;
}
