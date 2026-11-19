package com.veryal.application.moment;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.moment.Moment;
import com.veryal.domain.moment.MomentAudienceMode;
import com.veryal.domain.moment.MomentMedia;
import com.veryal.domain.moment.MomentRepository;
import com.veryal.domain.moment.MomentSetting;
import com.veryal.domain.moment.MomentSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 朋友圈访问策略。
 *
 * <p>信息流 SQL、详情、评论、点赞与媒体下载共用同一套规则：
 * 非好友不可见；私密仅作者可见；部分可见 / 不给谁看查名单；作者可设置可见时间范围。
 * 任何一条不满足都按「不存在」处理，避免泄露动态是否存在。</p>
 */
@Service
@RequiredArgsConstructor
public class MomentAccessPolicy {

    private final FriendRepository friendRepository;
    private final MomentRepository momentRepository;
    private final MomentSettingRepository settingRepository;
    private final TimeProvider timeProvider;

    public Moment requireViewable(long viewerId, long momentId) {
        Moment moment = momentRepository.findById(momentId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOMENT_NOT_FOUND));
        if (!canView(viewerId, moment)) {
            throw new BusinessException(ErrorCode.MOMENT_NOT_FOUND);
        }
        return moment;
    }

    public MomentMedia requireMedia(long viewerId, long momentId, long fileId) {
        requireViewable(viewerId, momentId);
        return momentRepository.findMedia(momentId, fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    public boolean canView(long viewerId, Moment moment) {
        if (moment.deleted()) {
            return false;
        }
        if (moment.ownedBy(viewerId)) {
            return true;
        }
        if (moment.hidden()) {
            return false;
        }
        if (friendRepository.find(viewerId, moment.authorId()).isEmpty()) {
            return false;
        }
        if (!withinVisibleDays(moment)) {
            return false;
        }
        return switch (moment.visibility()) {
            case ALL_FRIENDS -> true;
            case PRIVATE -> false;
            case PARTIAL -> momentRepository.audienceUserIds(moment.id(),
                    MomentAudienceMode.ALLOW).contains(viewerId);
            case EXCLUDE -> !momentRepository.audienceUserIds(moment.id(),
                    MomentAudienceMode.DENY).contains(viewerId);
        };
    }

    private boolean withinVisibleDays(Moment moment) {
        MomentSetting setting = settingRepository.find(moment.authorId()).orElse(null);
        if (setting == null || !setting.limited()) {
            return true;
        }
        return moment.createdAt().isAfter(
                timeProvider.now().minus(Duration.ofDays(setting.visibleDays())));
    }
}
