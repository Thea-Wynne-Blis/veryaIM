package com.veryal.access.http.controller;

import com.veryal.access.http.dto.UpdatePrivacyRequest;
import com.veryal.access.http.dto.UserPrivacyResponse;
import com.veryal.access.http.dto.UserProfileResponse;
import com.veryal.access.http.dto.UserSearchResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.UserSocialService;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.identity.UserPrivacy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * 用户搜索、主页与隐私设置。
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserSocialController {

    private final UserSocialService userSocialService;

    @Operation(summary = "搜索用户", description = "按用户名或昵称模糊搜索。受对方 `searchable` 隐私开关约束，被搜索不到的用户不会出现在结果里。")
    @GetMapping("/search")
    public ApiResponse<List<UserSearchResponse>> search(@CurrentUser AuthenticatedUser user,
                                                        @RequestParam String keyword,
                                                        @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(userSocialService.search(user.userId(), keyword,
                        Math.max(1, Math.min(limit, 50))).stream()
                .map(UserSearchResponse::from)
                .toList());
    }

    @Operation(summary = "查看他人资料", description = "返回公开资料。是否展示在线状态取决于对方隐私设置。")
    @GetMapping("/{userId}/profile")
    public ApiResponse<UserProfileResponse> profile(@CurrentUser AuthenticatedUser user,
                                                    @PathVariable long userId) {
        return ApiResponse.ok(UserProfileResponse.from(
                userSocialService.profile(user.userId(), userId)));
    }

    @Operation(summary = "我的隐私设置", description = "查询是否允许被搜索、加好友策略、是否展示在线状态与已读回执。")
    @GetMapping("/me/privacy")
    public ApiResponse<UserPrivacyResponse> privacy(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(UserPrivacyResponse.from(
                userSocialService.privacy(user.userId())));
    }

    @Operation(summary = "修改隐私设置", description = "`friendPolicy` 支持 REJECT（拒绝所有人）、VERIFY（需要验证）、DIRECT（直接通过）。")
    @PutMapping("/me/privacy")
    public ApiResponse<UserPrivacyResponse> updatePrivacy(@CurrentUser AuthenticatedUser user,
                                                          @RequestBody UpdatePrivacyRequest request) {
        UserPrivacy.FriendPolicy policy = null;
        if (request.friendPolicy() != null && !request.friendPolicy().isBlank()) {
            try {
                policy = UserPrivacy.FriendPolicy.valueOf(
                        request.friendPolicy().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid friendPolicy");
            }
        }
        return ApiResponse.ok(UserPrivacyResponse.from(userSocialService.updatePrivacy(
                user.userId(), new UserSocialService.PrivacyUpdate(request.searchByPhone(),
                        request.searchByUsername(), policy, request.showOnline(),
                        request.readReceipt(), request.invitedToGroup()))));
    }
}
