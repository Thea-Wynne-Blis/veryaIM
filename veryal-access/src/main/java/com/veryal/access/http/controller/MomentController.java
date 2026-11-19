package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateMomentCommentRequest;
import com.veryal.access.http.dto.LikeMomentRequest;
import com.veryal.access.http.dto.MarkMomentReadRequest;
import com.veryal.access.http.dto.MomentCommentResponse;
import com.veryal.access.http.dto.MomentNoticeResponse;
import com.veryal.access.http.dto.MomentResponse;
import com.veryal.access.http.dto.MomentSettingsResponse;
import com.veryal.access.http.dto.PublishMomentRequest;
import com.veryal.access.http.dto.UpdateMomentSettingsRequest;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.moment.MomentApplicationService;
import com.veryal.application.moment.MomentInteractionService;
import com.veryal.application.moment.MomentNoticeService;
import com.veryal.application.moment.MomentView;
import com.veryal.application.moment.PublishMomentCommand;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.core.CursorPage;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.ratelimit.RateLimit;
import com.veryal.common.ratelimit.RateLimitDimension;
import com.veryal.domain.moment.MomentMediaKind;
import com.veryal.domain.moment.MomentVisibility;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 朋友圈 API：发布、信息流、互动、通知与个人设置。
 */
@Tag(name = "朋友圈")
@RestController
@RequestMapping("/api/v1/moments")
@RequiredArgsConstructor
public class MomentController {

    private final MomentApplicationService momentService;
    private final MomentInteractionService interactionService;
    private final MomentNoticeService noticeService;

    @Operation(summary = "发布动态", description = "正文与媒体至少一个；可见范围支持所有朋友 / 私密 / 部分可见 / 不给谁看。")
    @PostMapping
    @RateLimit(policy = "moment-publish", dimension = RateLimitDimension.USER)
    public ApiResponse<MomentResponse> publish(@CurrentUser AuthenticatedUser user,
                                               @Valid @RequestBody PublishMomentRequest request) {
        return ApiResponse.ok(MomentResponse.from(
                momentService.publish(user.userId(), toCommand(request))));
    }

    @Operation(summary = "好友动态信息流", description = "按动态 ID 倒序游标分页，包含自己发布的动态。")
    @GetMapping("/feed")
    public ApiResponse<CursorPage<MomentResponse>> feed(@CurrentUser AuthenticatedUser user,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(map(momentService.feed(user.userId(), cursor, limit)));
    }

    @Operation(summary = "我的动态", description = "作者本人视角，包含私密动态。")
    @GetMapping("/mine")
    public ApiResponse<CursorPage<MomentResponse>> mine(@CurrentUser AuthenticatedUser user,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(map(momentService.mine(user.userId(), cursor, limit)));
    }

    @Operation(summary = "好友的动态主页", description = "非好友返回 403。")
    @GetMapping("/users/{userId}")
    public ApiResponse<CursorPage<MomentResponse>> byAuthor(
            @CurrentUser AuthenticatedUser user, @PathVariable long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(map(momentService.byAuthor(user.userId(), userId, cursor, limit)));
    }

    @Operation(summary = "动态详情", description = "无权查看按不存在处理。")
    @GetMapping("/{momentId}")
    public ApiResponse<MomentResponse> detail(@CurrentUser AuthenticatedUser user,
                                              @PathVariable long momentId) {
        return ApiResponse.ok(MomentResponse.from(
                momentService.detail(user.userId(), momentId)));
    }

    @Operation(summary = "删除动态", description = "仅作者本人，软删除。")
    @DeleteMapping("/{momentId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user,
                                    @PathVariable long momentId) {
        momentService.delete(user.userId(), momentId);
        return ApiResponse.ok();
    }

    @Operation(summary = "点赞 / 取消点赞", description = "幂等；重复点赞不会重复计数。")
    @PostMapping("/{momentId}/like")
    public ApiResponse<MomentResponse> like(@CurrentUser AuthenticatedUser user,
                                            @PathVariable long momentId,
                                            @RequestBody LikeMomentRequest request) {
        return ApiResponse.ok(MomentResponse.from(
                interactionService.like(user.userId(), momentId, request.liked())));
    }

    @Operation(summary = "评论列表", description = "按评论 ID 正序游标分页。")
    @GetMapping("/{momentId}/comments")
    public ApiResponse<CursorPage<MomentCommentResponse>> comments(
            @CurrentUser AuthenticatedUser user, @PathVariable long momentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        CursorPage<com.veryal.application.moment.MomentCommentView> page =
                interactionService.comments(user.userId(), momentId, cursor, limit);
        return ApiResponse.ok(new CursorPage<>(
                page.items().stream().map(MomentCommentResponse::from).toList(),
                page.nextCursor(), page.hasMore()));
    }

    @Operation(summary = "发表评论", description = "content 最多 500 字，经过敏感词校验。")
    @PostMapping("/{momentId}/comments")
    @RateLimit(policy = "moment-comment", dimension = RateLimitDimension.USER)
    public ApiResponse<MomentCommentResponse> comment(
            @CurrentUser AuthenticatedUser user, @PathVariable long momentId,
            @Valid @RequestBody CreateMomentCommentRequest request) {
        return ApiResponse.ok(MomentCommentResponse.from(interactionService.comment(
                user.userId(), momentId, request.content(), request.replyToCommentId())));
    }

    @Operation(summary = "删除评论", description = "评论作者或动态作者可删。")
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@CurrentUser AuthenticatedUser user,
                                           @PathVariable long commentId) {
        interactionService.deleteComment(user.userId(), commentId);
        return ApiResponse.ok();
    }

    @Operation(summary = "互动通知列表", description = "点赞、评论、回复；按通知 ID 倒序游标分页。")
    @GetMapping("/notices")
    public ApiResponse<CursorPage<MomentNoticeResponse>> notices(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        CursorPage<com.veryal.application.moment.MomentNoticeView> page =
                noticeService.list(user.userId(), cursor, limit);
        return ApiResponse.ok(new CursorPage<>(
                page.items().stream().map(MomentNoticeResponse::from).toList(),
                page.nextCursor(), page.hasMore()));
    }

    @Operation(summary = "未读互动通知数", description = "用于入口红点。")
    @GetMapping("/notices/unread")
    public ApiResponse<Long> unreadNotices(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(noticeService.unreadCount(user.userId()));
    }

    @Operation(summary = "全部标记互动通知已读")
    @PostMapping("/notices/read")
    public ApiResponse<Void> readNotices(@CurrentUser AuthenticatedUser user) {
        noticeService.markAllRead(user.userId());
        return ApiResponse.ok();
    }

    @Operation(summary = "读取朋友圈设置")
    @GetMapping("/settings")
    public ApiResponse<MomentSettingsResponse> settings(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(MomentSettingsResponse.from(momentService.settings(user.userId())));
    }

    @Operation(summary = "修改好友可见时间范围", description = "0 全部，3 / 30 / 180 天。")
    @PutMapping("/settings")
    public ApiResponse<MomentSettingsResponse> updateSettings(
            @CurrentUser AuthenticatedUser user,
            @RequestBody UpdateMomentSettingsRequest request) {
        return ApiResponse.ok(MomentSettingsResponse.from(
                momentService.updateSettings(user.userId(), request.visibleDays())));
    }

    @Operation(summary = "记录信息流已读位置", description = "仅向前推进，用于「有新动态」提示。")
    @PostMapping("/feed/read")
    public ApiResponse<Void> markFeedRead(@CurrentUser AuthenticatedUser user,
                                          @RequestBody MarkMomentReadRequest request) {
        momentService.markFeedRead(user.userId(), request.momentId());
        return ApiResponse.ok();
    }

    private PublishMomentCommand toCommand(PublishMomentRequest request) {
        List<PublishMomentCommand.Media> media = request.media() == null ? List.of()
                : request.media().stream()
                .map(item -> new PublishMomentCommand.Media(item.fileId(),
                        mediaKind(item.kind()), item.width(), item.height(),
                        item.durationSeconds()))
                .toList();
        return new PublishMomentCommand(request.content(), visibility(request.visibility()),
                request.audienceUserIds(), media);
    }

    private CursorPage<MomentResponse> map(CursorPage<MomentView> page) {
        return new CursorPage<>(page.items().stream().map(MomentResponse::from).toList(),
                page.nextCursor(), page.hasMore());
    }

    private MomentVisibility visibility(String value) {
        if (value == null || value.isBlank()) {
            return MomentVisibility.ALL_FRIENDS;
        }
        try {
            return MomentVisibility.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid visibility");
        }
    }

    /** kind 省略时返回 null，服务端按文件真实类型判断。 */
    private MomentMediaKind mediaKind(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MomentMediaKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid media kind");
        }
    }
}
