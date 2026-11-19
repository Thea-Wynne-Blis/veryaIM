package com.veryal.access.http.dto;

import com.veryal.application.moment.MomentMediaView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 动态媒体。内容通过独立鉴权地址读取：
 * {@code /api/v1/moments/{momentId}/media/{fileId}}，缩略图追加 {@code /thumb}。
 */
public record MomentMediaResponse(
        @Schema(description = "文件 ID") long fileId,
        @Schema(description = "IMAGE / VIDEO") String kind,
        @Schema(description = "展示顺序") int sortOrder,
        @Schema(description = "像素宽，未知为 0") int width,
        @Schema(description = "像素高，未知为 0") int height,
        @Schema(description = "视频时长（秒）") Integer durationSeconds
) {

    public static MomentMediaResponse from(MomentMediaView view) {
        return new MomentMediaResponse(view.fileId(), view.kind(), view.sortOrder(),
                view.width(), view.height(), view.durationSeconds());
    }
}
