package com.veryal.application.moment;

import com.veryal.domain.moment.MomentMediaKind;
import com.veryal.domain.moment.MomentVisibility;

import java.util.List;

/**
 * 发布动态的入参。媒体文件必须已由作者上传，服务层只做归属与类型校验。
 */
public record PublishMomentCommand(String content, MomentVisibility visibility,
                                   List<Long> audienceUserIds, List<Media> media) {

    /** 发布时提交的单个媒体。 */
    public record Media(long fileId, MomentMediaKind kind, int width, int height,
                        Integer durationSeconds) {
    }
}
