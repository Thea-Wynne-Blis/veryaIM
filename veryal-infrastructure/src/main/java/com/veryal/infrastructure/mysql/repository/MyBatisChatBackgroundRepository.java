package com.veryal.infrastructure.mysql.repository;

import com.veryal.common.core.TimeProvider;
import com.veryal.domain.chatbackground.ChatBackground;
import com.veryal.domain.chatbackground.ChatBackgroundRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.ConversationMapper;
import com.veryal.infrastructure.mysql.mapper.UserSettingMapper;
import com.veryal.infrastructure.mysql.po.ConversationMemberBackgroundPO;
import com.veryal.infrastructure.mysql.po.UserSettingPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 聊天背景仓储实现：全局在 {@code im_user_setting}，会话级在
 * {@code im_conversation_member} 的两列上。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisChatBackgroundRepository implements ChatBackgroundRepository {

    private final UserSettingMapper settingMapper;
    private final ConversationMapper conversationMapper;
    private final TimeProvider timeProvider;

    @Override
    public Optional<ChatBackground> findGlobal(long userId) {
        UserSettingPO po = settingMapper.selectById(userId);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(new ChatBackground(po.chatBgFileId, po.chatBgPreset));
    }

    @Override
    public void saveGlobal(long userId, ChatBackground background) {
        settingMapper.upsertBackground(userId, background.fileId(), background.preset(),
                SqlTime.toLocal(timeProvider.now()));
    }

    @Override
    public Optional<ChatBackground> findConversation(String conversationId, long userId) {
        return Optional.ofNullable(
                        conversationMapper.selectMemberBackground(conversationId, userId))
                .map(po -> new ChatBackground(po.chatBgFileId, po.chatBgPreset));
    }

    @Override
    public void saveConversation(String conversationId, long userId, ChatBackground background) {
        conversationMapper.updateMemberBackground(conversationId, userId,
                background.fileId(), background.preset());
    }

    @Override
    public Map<String, ChatBackground> findConversations(long userId) {
        List<ConversationMemberBackgroundPO> rows = conversationMapper.selectMemberBackgrounds(userId);
        Map<String, ChatBackground> result = new LinkedHashMap<>();
        for (ConversationMemberBackgroundPO row : rows) {
            ChatBackground background = new ChatBackground(row.chatBgFileId, row.chatBgPreset);
            if (!background.isEmpty()) {
                result.put(row.conversationId, background);
            }
        }
        return result;
    }
}
