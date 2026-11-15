package com.veryal.domain.moderation;

import java.time.Instant;

/**
 * 敏感词。
 */
public record SensitiveWord(long id, String word, boolean enabled, Instant createdAt) {
}
