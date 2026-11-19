package com.veryal.access.http.dto;

import com.veryal.application.moment.MomentSettingsView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 朋友圈个人设置。
 */
public record MomentSettingsResponse(
        @Schema(description = "好友可见天数：0 全部") int visibleDays,
        @Schema(description = "上次浏览到的最新动态 ID") long lastReadMomentId,
        @Schema(description = "可选天数") List<Integer> supportedDays
) {

    public static MomentSettingsResponse from(MomentSettingsView view) {
        return new MomentSettingsResponse(view.visibleDays(), view.lastReadMomentId(),
                view.supportedDays());
    }
}
