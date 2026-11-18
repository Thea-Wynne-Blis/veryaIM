package com.veryal.application.favorite;

import java.time.Instant;

/**
 * 收藏视图。文本类消息带 {@code text}，附件类带 {@code fileId/fileName}。
 */
public record FavoriteView(long id, String conversationId, long messageId, String type,
                           String text, Long fileId, String fileName, Long sizeBytes,
                           String contentType, long senderId, String senderName,
                           String conversationTitle, Instant createdAt) {
}
