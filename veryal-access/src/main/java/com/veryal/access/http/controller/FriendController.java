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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 好友申请与好友管理。
 */
@Tag(name = "好友")
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendApplicationService friendApplicationService;

    @Operation(summary = "发起好友申请", description = "向目标用户发送申请，可附验证消息。对方 `friendPolicy=DIRECT` 时直接成为好友，返回结果中会体现。")
    @PostMapping("/requests")
    public ApiResponse<FriendRequestResponse> sendRequest(@CurrentUser AuthenticatedUser user,
                                                          @Valid @RequestBody CreateFriendRequest request) {
        return ApiResponse.ok(FriendRequestResponse.from(friendApplicationService.sendRequest(
                user.userId(), request.toUserId(), request.message())));
    }

    @Operation(summary = "我的好友申请", description = "默认返回收到的申请；可通过参数查看已发出的申请。")
    @GetMapping("/requests")
    public ApiResponse<List<FriendRequestResponse>> requests(@CurrentUser AuthenticatedUser user,
                                                             @RequestParam(defaultValue = "incoming")
                                                             String direction,
                                                             @RequestParam(defaultValue = "50") int limit) {
        List<FriendRequestView> views = "outgoing".equalsIgnoreCase(direction)
                ? friendApplicationService.outgoing(user.userId(), limit)
                : friendApplicationService.incoming(user.userId(), limit);
        return ApiResponse.ok(views.stream().map(FriendRequestResponse::from).toList());
    }

    @Operation(summary = "同意好友申请", description = "成为好友并双向写入好友关系。")
    @PostMapping("/requests/{requestId}/accept")
    public ApiResponse<FriendRequestResponse> accept(@CurrentUser AuthenticatedUser user,
                                                     @PathVariable long requestId) {
        return ApiResponse.ok(FriendRequestResponse.from(friendApplicationService.accept(
                user.userId(), requestId)));
    }

    @Operation(summary = "拒绝好友申请", description = "申请置为已拒绝，不会建立好友关系。")
    @PostMapping("/requests/{requestId}/reject")
    public ApiResponse<FriendRequestResponse> reject(@CurrentUser AuthenticatedUser user,
                                                     @PathVariable long requestId) {
        return ApiResponse.ok(FriendRequestResponse.from(friendApplicationService.reject(
                user.userId(), requestId)));
    }

    @Operation(summary = "撤回好友申请", description = "仅申请人可撤回尚未处理的申请。")
    @PostMapping("/requests/{requestId}/cancel")
    public ApiResponse<FriendRequestResponse> cancel(@CurrentUser AuthenticatedUser user,
                                                     @PathVariable long requestId) {
        return ApiResponse.ok(FriendRequestResponse.from(friendApplicationService.cancel(
                user.userId(), requestId)));
    }

    @Operation(summary = "好友列表", description = "返回全部好友，含备注名与在线状态。")
    @GetMapping
    public ApiResponse<List<FriendResponse>> friends(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(friendApplicationService.listFriends(user.userId())
                .stream().map(FriendResponse::from).toList());
    }

    @Operation(summary = "设置好友备注", description = "修改备注名，仅自己可见。")
    @PutMapping("/{friendId}")
    public ApiResponse<Void> updateRemark(@CurrentUser AuthenticatedUser user,
                                          @PathVariable long friendId,
                                          @Valid @RequestBody UpdateFriendRemarkRequest request) {
        friendApplicationService.updateRemark(user.userId(), friendId,
                request.remark());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除好友", description = "双向解除好友关系，历史会话保留但不能再发消息。")
    @DeleteMapping("/{friendId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user,
                                    @PathVariable long friendId) {
        friendApplicationService.deleteFriend(user.userId(), friendId);
        return ApiResponse.ok();
    }
}
