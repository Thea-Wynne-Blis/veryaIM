package com.veryal.application.moment;

import java.util.List;

/**
 * 朋友圈个人设置视图。
 *
 * @param lastReadMomentId 上次浏览信息流时看到的最新动态 ID
 */
public record MomentSettingsView(int visibleDays, long lastReadMomentId,
                                 List<Integer> supportedDays) {
}
