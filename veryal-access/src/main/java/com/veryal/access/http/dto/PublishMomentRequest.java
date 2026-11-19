package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发布朋友圈动态。正文与媒体至少要有一个。
 */
public record PublishMomentRequest(
        @Schema(description = "正文，最多 2000 字")
        @Size(max = 2000) String content,
        @Schema(description = "可见范围：ALL_FRIENDS / PRIVATE / PARTIAL / EXCLUDE",
                allowableValues = {"ALL_FRIENDS", "PRIVATE", "PARTIAL", "EXCLUDE"})
        String visibility,
        @Schema(description = "「部分可见」或「不给谁看」的好友 ID 列表")
        List<Long> audienceUserIds,
        @Schema(description = "媒体列表，最多 9 个")
        @Size(max = 9) List<MediaRequest> media
) {

    /** 已上传文件引用。kind 可省略，服务端按文件类型判断。 */
    public record MediaRequest(
            @Schema(description = "文件 ID") long fileId,
            @Schema(description = "IMAGE / VIDEO") String kind,
            @Schema(description = "像素宽") int width,
            @Schema(description = "像素高") int height,
            @Schema(description = "视频时长（秒）") Integer durationSeconds
    ) {
    }
}
