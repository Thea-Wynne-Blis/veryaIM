package com.veryal.domain.moment;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 个人朋友圈设置。
 *
 * @param visibleDays       好友可见的时间范围：0 全部，3 / 30 / 180 天
 * @param lastReadMomentId  上一次打开信息流时看到的最新动态 ID，用于「有新动态」提示
 */
public record MomentSetting(long userId, int visibleDays, long lastReadMomentId,
                            Instant updatedAt) {

    public static final Set<Integer> SUPPORTED_DAYS = Set.of(0, 3, 30, 180);

    public MomentSetting {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (!SUPPORTED_DAYS.contains(visibleDays)) {
            throw new IllegalArgumentException("unsupported visible days: " + visibleDays);
        }
        if (lastReadMomentId < 0) {
            lastReadMomentId = 0;
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
    }

    public static MomentSetting defaults(long userId, Instant now) {
        return new MomentSetting(userId, 0, 0, now);
    }

    public MomentSetting withVisibleDays(int newVisibleDays, Instant now) {
        return new MomentSetting(userId, newVisibleDays, lastReadMomentId, now);
    }

    public MomentSetting withLastRead(long momentId, Instant now) {
        return new MomentSetting(userId, visibleDays,
                Math.max(lastReadMomentId, momentId), now);
    }

    /** 作者是否限制了好友可查看的时间范围。 */
    public boolean limited() {
        return visibleDays > 0;
    }

    public static List<Integer> supportedDays() {
        return List.of(0, 3, 30, 180);
    }
}
