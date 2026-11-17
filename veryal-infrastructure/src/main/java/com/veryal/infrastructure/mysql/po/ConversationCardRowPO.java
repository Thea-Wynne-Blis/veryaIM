package com.veryal.infrastructure.mysql.po;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会话卡片批量查询的行投影，对应 {@code ConversationMapper.selectCardRows}。
 */
@Getter
@Setter
public class ConversationCardRowPO {

    public String conversationId;
    public int conversationType;
    public LocalDateTime updatedAt;
    public long lastReadSeq;
    public long lastSeq;
    public long clearedSeq;
    public boolean manualUnread;
    public boolean pinned;
    public boolean muted;
    public boolean hidden;
    public int memberCount;
    public Integer lastMessageType;
    public byte[] lastMessageContent;
    public LocalDateTime lastMessageRecalledAt;
    public LocalDateTime lastMessageCreatedAt;
    public String groupName;
    public Long peerUserId;
    public String peerNickname;
}
