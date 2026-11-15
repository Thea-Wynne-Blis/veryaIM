package com.veryal.access.http.controller;

import com.veryal.access.http.dto.BlockedUserResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.friend.FriendApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 黑名单管理。
 */
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final FriendApplicationService friendApplicationService;

    @GetMapping
    public ApiResponse<List<BlockedUserResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(friendApplicationService.listBlocked(user.tenantId(), user.userId())
                .stream().map(BlockedUserResponse::from).toList());
    }

    @PostMapping("/{userId}")
    public ApiResponse<Void> block(@CurrentUser AuthenticatedUser user, @PathVariable long userId) {
        friendApplicationService.block(user.tenantId(), user.userId(), userId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unblock(@CurrentUser AuthenticatedUser user, @PathVariable long userId) {
        friendApplicationService.unblock(user.tenantId(), user.userId(), userId);
        return ApiResponse.ok();
    }
}
