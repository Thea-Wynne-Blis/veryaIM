package com.veryal.application.moment;

import com.veryal.common.core.CursorPage;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.moment.Moment;
import com.veryal.domain.moment.MomentAudience;
import com.veryal.domain.moment.MomentAudienceMode;
import com.veryal.domain.moment.MomentMedia;
import com.veryal.domain.moment.MomentMediaKind;
import com.veryal.domain.moment.MomentRepository;
import com.veryal.domain.moment.MomentSetting;
import com.veryal.domain.moment.MomentSettingRepository;
import com.veryal.domain.moment.MomentStatus;
import com.veryal.domain.moment.MomentVisibility;
import com.veryal.application.file.FileApplicationService;
import com.veryal.application.file.FileView;
import com.veryal.application.moderation.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 朋友圈动态：发布、删除、信息流、主页、设置与媒体鉴权。
 */
@Service
@RequiredArgsConstructor
public class MomentApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_AUDIENCE_SIZE = 200;

    private final MomentRepository momentRepository;
    private final MomentSettingRepository settingRepository;
    private final MomentAccessPolicy accessPolicy;
    private final MomentViewAssembler assembler;
    private final MomentNoticeService noticeService;
    private final MomentThumbnailWarmer thumbnailWarmer;
    private final FriendRepository friendRepository;
    private final FileApplicationService fileApplicationService;
    private final SensitiveWordService sensitiveWordService;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public MomentView publish(long authorId, PublishMomentCommand command) {
        String content = command.content() == null ? "" : command.content().trim();
        List<PublishMomentCommand.Media> mediaInputs = command.media() == null
                ? List.of() : command.media();
        if (content.isEmpty() && mediaInputs.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "content or media is required");
        }
        if (content.length() > Moment.MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "content exceeds " + Moment.MAX_CONTENT_LENGTH + " characters");
        }
        if (mediaInputs.size() > Moment.MAX_MEDIA_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "at most " + Moment.MAX_MEDIA_COUNT + " media are allowed");
        }
        sensitiveWordService.checkText(content);

        MomentVisibility visibility = command.visibility() == null
                ? MomentVisibility.ALL_FRIENDS : command.visibility();
        Instant now = timeProvider.now();
        long momentId = idGenerator.nextId();
        List<MomentAudience> audience = audience(momentId, authorId, visibility,
                command.audienceUserIds());

        momentRepository.save(new Moment(momentId, authorId, content, visibility, 0, 0,
                MomentStatus.NORMAL, now, null));
        if (!mediaInputs.isEmpty()) {
            List<MomentMedia> media = new ArrayList<>(mediaInputs.size());
            int order = 0;
            for (PublishMomentCommand.Media input : mediaInputs) {
                media.add(toMedia(momentId, order++, authorId, input));
            }
            momentRepository.saveMedia(media);
            warmThumbnails(media);
        }
        if (!audience.isEmpty()) {
            momentRepository.saveAudience(audience);
        }
        return assembler.assembleOne(authorId, requireMoment(momentId));
    }

    /** 图片的缩略图放到后台预热；视频没有缩略图，直接跳过。 */
    private void warmThumbnails(List<MomentMedia> media) {
        for (MomentMedia item : media) {
            if (item.kind() == MomentMediaKind.IMAGE) {
                thumbnailWarmer.warm(item.fileId());
            }
        }
    }

    @Transactional
    public void delete(long userId, long momentId) {
        Moment moment = requireMoment(momentId);
        if (moment.deleted()) {
            throw new BusinessException(ErrorCode.MOMENT_NOT_FOUND);
        }
        if (!moment.ownedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only author can delete moment");
        }
        momentRepository.softDelete(momentId, timeProvider.now());
        noticeService.discardMoment(momentId);
    }

    @Transactional(readOnly = true)
    public CursorPage<MomentView> feed(long viewerId, String cursor, int limit) {
        return page(viewerId, null, cursor, limit);
    }

    @Transactional(readOnly = true)
    public CursorPage<MomentView> byAuthor(long viewerId, long authorId, String cursor, int limit) {
        if (authorId != viewerId && friendRepository.find(viewerId, authorId).isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only friends' moments are visible");
        }
        return page(viewerId, authorId, cursor, limit);
    }

    @Transactional(readOnly = true)
    public CursorPage<MomentView> mine(long userId, String cursor, int limit) {
        return page(userId, userId, cursor, limit);
    }

    @Transactional(readOnly = true)
    public MomentView detail(long viewerId, long momentId) {
        return assembler.assembleOne(viewerId, accessPolicy.requireViewable(viewerId, momentId));
    }

    /**
     * 媒体下载前的鉴权：动态可见 + 文件确实挂在该动态下。
     */
    public void requireMedia(long viewerId, long momentId, long fileId) {
        accessPolicy.requireMedia(viewerId, momentId, fileId);
    }

    @Transactional(readOnly = true)
    public MomentSettingsView settings(long userId) {
        MomentSetting setting = settingRepository.find(userId).orElse(null);
        int visibleDays = setting == null ? 0 : setting.visibleDays();
        long lastRead = setting == null ? 0 : setting.lastReadMomentId();
        return new MomentSettingsView(visibleDays, lastRead, MomentSetting.supportedDays());
    }

    @Transactional
    public MomentSettingsView updateSettings(long userId, int visibleDays) {
        if (!MomentSetting.SUPPORTED_DAYS.contains(visibleDays)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "unsupported visible days");
        }
        Instant now = timeProvider.now();
        MomentSetting setting = settingRepository.find(userId)
                .orElseGet(() -> MomentSetting.defaults(userId, now));
        settingRepository.save(setting.withVisibleDays(visibleDays, now));
        return settings(userId);
    }

    /**
     * 记录信息流已读位置，仅向前推进，客户端重复提交不会回退。
     */
    @Transactional
    public void markFeedRead(long userId, long momentId) {
        if (momentId <= 0) {
            return;
        }
        Instant now = timeProvider.now();
        MomentSetting setting = settingRepository.find(userId)
                .orElseGet(() -> MomentSetting.defaults(userId, now));
        settingRepository.save(setting.withLastRead(momentId, now));
    }

    private CursorPage<MomentView> page(long viewerId, Long authorId, String cursor, int limit) {
        int size = pageSize(limit);
        long cursorId = parseCursor(cursor);
        List<Moment> moments = new ArrayList<>(momentRepository.feed(viewerId, authorId, cursorId,
                size + 1, timeProvider.now()));
        boolean hasMore = moments.size() > size;
        if (hasMore) {
            moments = moments.subList(0, size);
        }
        List<MomentView> items = assembler.assemble(viewerId, moments);
        String nextCursor = hasMore ? Long.toString(moments.get(moments.size() - 1).id()) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private List<MomentAudience> audience(long momentId, long authorId,
                                          MomentVisibility visibility, List<Long> requested) {
        if (visibility != MomentVisibility.PARTIAL && visibility != MomentVisibility.EXCLUDE) {
            return List.of();
        }
        List<Long> userIds = requested == null ? List.of() : requested.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "audience is required for partial or excluded visibility");
        }
        if (userIds.size() > MAX_AUDIENCE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "at most " + MAX_AUDIENCE_SIZE + " audience users are allowed");
        }
        Set<Long> friends = friendRepository.listByUser(authorId, 500).stream()
                .map(Friend::friendId)
                .collect(Collectors.toSet());
        for (Long userId : userIds) {
            if (!friends.contains(userId)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "audience must be friends of the author");
            }
        }
        MomentAudienceMode mode = visibility == MomentVisibility.PARTIAL
                ? MomentAudienceMode.ALLOW : MomentAudienceMode.DENY;
        return userIds.stream()
                .map(userId -> new MomentAudience(momentId, userId, mode))
                .toList();
    }

    private MomentMedia toMedia(long momentId, int order, long authorId,
                                PublishMomentCommand.Media input) {
        if (input.fileId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid media file id");
        }
        FileView file = fileApplicationService.ownedFile(authorId, input.fileId());
        String contentType = file.contentType() == null ? ""
                : file.contentType().toLowerCase(Locale.ROOT);
        boolean video = contentType.startsWith("video/");
        if (!video && !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "moment media must be image or video");
        }
        MomentMediaKind kind = video ? MomentMediaKind.VIDEO : MomentMediaKind.IMAGE;
        if (input.kind() != null && input.kind() != kind) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "declared media type does not match file");
        }
        return new MomentMedia(idGenerator.nextId(), momentId, input.fileId(), order, kind,
                Math.max(0, input.width()), Math.max(0, input.height()),
                video ? input.durationSeconds() : null);
    }

    private Moment requireMoment(long momentId) {
        return momentRepository.findById(momentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOMENT_NOT_FOUND));
    }

    private int pageSize(int limit) {
        return limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            long value = Long.parseLong(cursor.trim());
            return value <= 0 ? Long.MAX_VALUE : value;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid cursor");
        }
    }
}
