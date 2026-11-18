package com.veryal.domain.group;

import java.time.Instant;

/**
 * 群成员禁言状态。
 *
 * <p>{@code mutedUntil} 为 {@code null} 表示永久禁言；行不存在表示未禁言。
 * 两者必须区分开，因此这里不能用 {@code Optional<Instant>} 表达。</p>
 */
public record GroupMute(String groupId, long userId, Instant mutedUntil) {

    public static GroupMute until(String groupId, long userId, Instant mutedUntil) {
        return new GroupMute(groupId, userId, mutedUntil);
    }

    public static GroupMute forever(String groupId, long userId) {
        return new GroupMute(groupId, userId, null);
    }

    public boolean permanent() {
        return mutedUntil == null;
    }

    /** 在给定时刻是否仍处于禁言中。 */
    public boolean activeAt(Instant now) {
        return permanent() || mutedUntil.isAfter(now);
    }
}
