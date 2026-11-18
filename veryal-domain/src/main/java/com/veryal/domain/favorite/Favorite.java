package com.veryal.domain.favorite;

import java.time.Instant;

/**
 * 一条收藏。内容在收藏时快照下来，之后原消息撤回也不影响查看。
 *
 * @param msgType  原消息类型码（1 TEXT / 2 IMAGE / 3 FILE / 4 AUDIO / 5 VIDEO / 6 EMOJI）
 * @param fileId   附件消息引用的文件 ID，非附件为 {@code null}
 * @param fileName 附件原始文件名，非附件为 {@code null}
 */
public record Favorite(long id, long userId, String conversationId, long messageId,
                       int msgType, byte[] content, Long fileId, String fileName,
                       Long sizeBytes, String contentType, long senderId, String senderName,
                       String conversationTitle, Instant createdAt) {
}
