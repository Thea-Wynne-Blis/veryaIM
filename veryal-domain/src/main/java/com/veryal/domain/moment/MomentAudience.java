package com.veryal.domain.moment;

/**
 * 可见名单项。{@code ALLOW} 用于「部分可见」，{@code DENY} 用于「不给谁看」。
 */
public record MomentAudience(long momentId, long userId, MomentAudienceMode mode) {

    public MomentAudience {
        if (momentId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("audience ids must be positive");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
    }
}
