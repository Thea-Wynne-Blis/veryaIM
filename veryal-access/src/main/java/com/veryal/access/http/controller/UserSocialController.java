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

/**
 * 用户搜索、主页与隐私设置。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserSocialController {

    private final UserSocialService userSocialService;

    @GetMapping("/search")
    public ApiResponse<List<UserSearchResponse>> search(@CurrentUser AuthenticatedUser user,
                                                        @RequestParam String keyword,
                                                        @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(userSocialService.search(user.tenantId(), user.userId(), keyword,
                        Math.max(1, Math.min(limit, 50))).stream()
                .map(UserSearchResponse::from)
                .toList());
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<UserProfileResponse> profile(@CurrentUser AuthenticatedUser user,
                                                    @PathVariable long userId) {
        return ApiResponse.ok(UserProfileResponse.from(
                userSocialService.profile(user.tenantId(), user.userId(), userId)));
    }

    @GetMapping("/me/privacy")
    public ApiResponse<UserPrivacyResponse> privacy(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(UserPrivacyResponse.from(
                userSocialService.privacy(user.userId())));
    }

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
