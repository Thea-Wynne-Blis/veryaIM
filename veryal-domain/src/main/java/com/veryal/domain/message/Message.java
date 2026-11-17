package com.veryal.domain.message;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * 消息聚合根。
 *
 * <p>客户端消息 ID 用于幂等；会话序号由服务端分配，是消息排序的唯一依据。</p>
 */
public record Message(long id, String conversationId, long senderId, String clientMsgId,
                      long seq, MessageType type, byte[] content, Instant createdAt,
                      Instant recalledAt, Long recalledBy,
                      Long replyToId, Long replySenderId, String replyPreview) {

    public Message {
        if (id <= 0) {
            throw new IllegalArgumentException("message id must be positive");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversation id must not be blank");
        }
        if (senderId <= 0) {
            throw new IllegalArgumentException("sender id must be positive");
        }
        if (clientMsgId == null || clientMsgId.isBlank()) {
            throw new IllegalArgumentException("client message id must not be blank");
        }
        if (seq <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (type == null) {
            throw new IllegalArgumentException("message type must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if ((recalledAt == null) != (recalledBy == null)) {
            throw new IllegalArgumentException("recalledAt and recalledBy must both be set or both be null");
        }
        boolean hasReply = replyToId != null || replySenderId != null || replyPreview != null;
        if (hasReply && (replyToId == null || replySenderId == null
                || replyPreview == null || replyPreview.isBlank())) {
            throw new IllegalArgumentException("reply fields must be either all set or all null");
        }
    }

    public static Message create(long id, String conversationId, long senderId,
                                 String clientMsgId, long seq, MessageType type, byte[] content,
                                 Instant now) {
        return create(id, conversationId, senderId, clientMsgId, seq, type, content, now,
                null, null, null);
    }

    public static Message create(long id, String conversationId, long senderId,
                                 String clientMsgId, long seq, MessageType type, byte[] content,
                                 Instant now, Long replyToId, Long replySenderId,
                                 String replyPreview) {
        return new Message(id, conversationId, senderId, clientMsgId, seq, type, content, now,
                null, null, replyToId, replySenderId, replyPreview);
    }

    public Message recall(long operatorId, Instant now) {
        if (recalledAt != null) {
            return this;
        }
        return new Message(id, conversationId, senderId, clientMsgId, seq, type, content,
                createdAt, now, operatorId, replyToId, replySenderId, replyPreview);
    }

    public boolean quoted() {
        return replyToId != null;
    }

    public boolean recalled() {
        return recalledAt != null;
    }

    public String textContent() {
        return new String(content, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Message message)) {
            return false;
        }
        return id == message.id
                && senderId == message.senderId && clientMsgId.equals(message.clientMsgId)
                && seq == message.seq && type == message.type && Arrays.equals(content, message.content)
                && createdAt.equals(message.createdAt)
                && Objects.equals(recalledAt, message.recalledAt)
                && Objects.equals(recalledBy, message.recalledBy)
                && Objects.equals(replyToId, message.replyToId)
                && Objects.equals(replySenderId, message.replySenderId)
                && Objects.equals(replyPreview, message.replyPreview);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(id);
        result = 31 * result + conversationId.hashCode();
        result = 31 * result + Long.hashCode(seq);
        result = 31 * result + Arrays.hashCode(content);
        result = 31 * result + Objects.hashCode(recalledAt);
        result = 31 * result + Objects.hashCode(replyToId);
        return result;
    }
}
