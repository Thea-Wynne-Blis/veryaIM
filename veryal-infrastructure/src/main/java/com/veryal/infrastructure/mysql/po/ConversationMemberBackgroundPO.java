package com.veryal.infrastructure.mysql.po;

import lombok.Getter;
import lombok.Setter;

/**
 * 会话级聊天背景投影（im_conversation_member 的两列）。
 */
@Getter
@Setter
public class ConversationMemberBackgroundPO {

    public String conversationId;
    public Long chatBgFileId;
    public String chatBgPreset;
}
