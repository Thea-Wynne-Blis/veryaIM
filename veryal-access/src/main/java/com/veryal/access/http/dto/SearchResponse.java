package com.veryal.access.http.dto;

import com.veryal.application.search.SearchView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 全局搜索响应：联系人、群聊、聊天记录、文件。
 */
public record SearchResponse(List<ContactHit> contacts, List<GroupHit> groups,
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
                          String conversationTitle, String name, String type,
                          long sizeBytes, String contentType, long senderId, String senderName,
                          Instant createdAt) {
    }

    public static SearchResponse from(SearchView view) {
        List<ContactHit> contacts = view.contacts().stream()
                .map(hit -> new ContactHit(hit.userId(), hit.name(), hit.nickname(), hit.remark(),
                        hit.avatar(), hit.online()))
                .toList();
        List<GroupHit> groups = view.groups().stream()
                .map(hit -> new GroupHit(hit.conversationId(), hit.title(), hit.memberCount()))
                .toList();
        List<MessageHit> messages = view.messages().stream()
                .map(hit -> new MessageHit(hit.messageId(), hit.conversationId(),
                        hit.conversationTitle(), hit.senderId(), hit.senderName(), hit.text(),
                        hit.createdAt()))
                .toList();
        List<FileHit> files = view.files().stream()
                .map(hit -> new FileHit(hit.fileId(), hit.messageId(), hit.conversationId(),
                        hit.conversationTitle(), hit.name(), hit.messageType(), hit.sizeBytes(),
                        hit.contentType(), hit.senderId(), hit.senderName(), hit.createdAt()))
                .toList();
        return new SearchResponse(contacts, groups, messages, files);
    }
}
