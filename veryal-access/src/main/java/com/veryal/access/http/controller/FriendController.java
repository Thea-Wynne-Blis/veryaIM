package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateFriendRequest;
import com.veryal.access.http.dto.FriendRequestResponse;
import com.veryal.access.http.dto.FriendResponse;
import com.veryal.access.http.dto.UpdateFriendRemarkRequest;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.friend.FriendApplicationService;
import com.veryal.application.friend.FriendRequestView;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
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

/**
 * 好友申请与好友管理。
 */
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendApplicationService friendApplicationService;

    @PostMapping("/requests")
    public ApiResponse<FriendRequestResponse> sendRequest(@CurrentUser AuthenticatedUser user,
                                                          @Valid @RequestBody CreateFriendRequest request) {
        return ApiResponse.ok(FriendRequestResponse.from(friendApplicationService.sendRequest(
                user.tenantId(), user.userId(), request.toUserId(), request.message())));
    }

    @GetMapping("/requests")
    public ApiResponse<List<FriendRequestResponse>> requests(@CurrentUser AuthenticatedUser user,
                                                             @RequestParam(defaultValue = "incoming")
                                                             String direction,
                                                             @RequestParam(defaultValue = "50") int limit) {
        List<FriendRequestView> views = "outgoing".equalsIgnoreCase(direction)
                ? friendApplicationService.outgoing(user.tenantId(), user.userId(), limit)
                : friendApplicationService.incoming(user.tenantId(), user.userId(), limit);
        return ApiResponse.ok(views.stream().map(FriendRequestResponse::from).toList());
    }

    @PostMapping("/requests/{requestId}/accept")
    public ApiResponse<FriendRequestResponse> accept(@CurrentUser AuthenticatedUser user,
                                                     @PathVariable long requestId) {
        return ApiResponse.ok(FriendRequestResponse.from(friendApplicationService.accept(
                user.tenantId(), user.userId(), requestId)));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ApiResponse<FriendRequestResponse> reject(@CurrentUser AuthenticatedUser user,
                                                     @PathVariable long requestId) {
        return ApiResponse.ok(FriendRequestResponse.from(friendApplicationService.reject(
                user.tenantId(), user.userId(), requestId)));
    }

    @PostMapping("/requests/{requestId}/cancel")
    public ApiResponse<FriendRequestResponse> cancel(@CurrentUser AuthenticatedUser user,
                                                     @PathVariable long requestId) {
        return ApiResponse.ok(FriendRequestResponse.from(friendApplicationService.cancel(
                user.tenantId(), user.userId(), requestId)));
    }

    @GetMapping
    public ApiResponse<List<FriendResponse>> friends(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(friendApplicationService.listFriends(user.tenantId(), user.userId())
                .stream().map(FriendResponse::from).toList());
    }

    @PutMapping("/{friendId}")
    public ApiResponse<Void> updateRemark(@CurrentUser AuthenticatedUser user,
                                          @PathVariable long friendId,
                                          @Valid @RequestBody UpdateFriendRemarkRequest request) {
        friendApplicationService.updateRemark(user.tenantId(), user.userId(), friendId,
                request.remark());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{friendId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user,
                                    @PathVariable long friendId) {
        friendApplicationService.deleteFriend(user.tenantId(), user.userId(), friendId);
        return ApiResponse.ok();
    }
}
