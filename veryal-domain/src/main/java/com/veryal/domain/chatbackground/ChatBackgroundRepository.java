package com.veryal.domain.chatbackground;

import java.util.Map;
import java.util.Optional;

/**
 * 聊天背景仓储端口。
 *
 * <p>全局背景存在 {@code im_user_setting}，会话级覆盖存在 {@code im_conversation_member}，
 * 由基础设施层用同一条实现串起来，应用层不关心具体落在哪张表。</p>
 */
public interface ChatBackgroundRepository {

    Optional<ChatBackground> findGlobal(long userId);

    void saveGlobal(long userId, ChatBackground background);

    Optional<ChatBackground> findConversation(String conversationId, long userId);

    void saveConversation(String conversationId, long userId, ChatBackground background);

    /** 用户在全部会话上的覆盖；没有覆盖的会话不会出现在结果里。 */
    Map<String, ChatBackground> findConversations(long userId);
}
