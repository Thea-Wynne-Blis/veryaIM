package com.veryal.access.http.dto;

import com.veryal.domain.moderation.SensitiveWord;

import java.time.Instant;

/**
 * 敏感词响应。
 */
public record SensitiveWordResponse(long id, String word, boolean enabled, Instant createdAt) {

    public static SensitiveWordResponse from(SensitiveWord word) {
        return new SensitiveWordResponse(word.id(), word.word(), word.enabled(),
                word.createdAt());
    }
}
