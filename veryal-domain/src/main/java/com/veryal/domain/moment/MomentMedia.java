package com.veryal.domain.moment;

/**
 * 动态中的一张图片或一个视频。
 *
 * @param width           图片/视频像素宽，客户端探测失败时为 0
 * @param height          图片/视频像素高，客户端探测失败时为 0
 * @param durationSeconds 视频时长（秒），图片为 {@code null}
 */
public record MomentMedia(long id, long momentId, long fileId, int sortOrder,
                          MomentMediaKind kind, int width, int height,
                          Integer durationSeconds) {

    public MomentMedia {
        if (id <= 0 || momentId <= 0 || fileId <= 0) {
            throw new IllegalArgumentException("moment media ids must be positive");
        }
        if (kind == null) {
            kind = MomentMediaKind.IMAGE;
        }
        if (sortOrder < 0) {
            sortOrder = 0;
        }
        if (width < 0) {
            width = 0;
        }
        if (height < 0) {
            height = 0;
        }
    }
}
