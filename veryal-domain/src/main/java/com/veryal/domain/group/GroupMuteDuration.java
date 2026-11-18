package com.veryal.domain.group;

import java.time.Duration;
import java.util.Locale;

/**
 * 群成员禁言档位。
 *
 * <p>只保留三档：7 分钟、30 分钟、永久。{@link #UNMUTE} 不是时长，用于解除禁言，
 * 这样「设置」和「解除」走同一个接口，前端不需要两套调用。</p>
 */
public enum GroupMuteDuration {

    MUTE_7M(Duration.ofMinutes(7)),
    MUTE_30M(Duration.ofMinutes(30)),
    MUTE_FOREVER(null),
    UNMUTE(Duration.ZERO);

    private final Duration duration;

    GroupMuteDuration(Duration duration) {
        this.duration = duration;
    }

    public boolean permanent() {
        return this == MUTE_FOREVER;
    }

    public boolean unmute() {
        return this == UNMUTE;
    }

    /**
     * 限时时长；永久与解除禁言返回 {@code null}。
     */
    public Duration duration() {
        return duration;
    }

    /**
     * 解析请求里的档位，容忍大小写和连字符（{@code mute-7m}）。
     */
    public static GroupMuteDuration fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mute duration is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (GroupMuteDuration candidate : values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unsupported mute duration: " + value);
    }
}
