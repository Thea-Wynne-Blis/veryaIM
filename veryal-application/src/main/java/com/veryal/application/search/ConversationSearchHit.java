package com.veryal.application.search;

import java.time.Instant;

public record ConversationSearchHit(long messageId, long seq, long senderId, String senderName,
                                     String type, String text, Instant createdAt,
                                     Long fileId, String fileName, Long sizeBytes, String contentType) {
}
