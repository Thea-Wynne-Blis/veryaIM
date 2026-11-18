package com.veryal.application.search;

import java.time.Instant;
import java.util.List;

/**
 * 全局搜索结果：联系人 / 群聊 / 聊天记录 / 文件，一次返回。
 *
 * <p>范围只限「我」的数据：好友、我加入的群、我参与的会话，绝不会搜到别人的消息。</p>
 */
public record SearchView(List<ContactHit> contacts, List<GroupHit> groups,
                         List<MessageHit> messages, List<FileHit> files) {

    public record ContactHit(long userId, String name, String nickname, String remark,
                             String avatar, boolean online) {
    }

    public record GroupHit(String conversationId, String title, int memberCount) {
    }

    public record MessageHit(long messageId, String conversationId, String conversationTitle,
                             long senderId, String senderName, String text, Instant createdAt) {
    }

    public record FileHit(long fileId, long messageId, String conversationId,
                          String conversationTitle, String name, String messageType,
                          long sizeBytes, String contentType, long senderId, String senderName,
                          Instant createdAt) {
    }
}
