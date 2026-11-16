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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 黑名单管理。
 */
@Tag(name = "黑名单")
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final FriendApplicationService friendApplicationService;

    @Operation(summary = "黑名单列表", description = "返回被我拉黑的全部用户。")
    @GetMapping
    public ApiResponse<List<BlockedUserResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(friendApplicationService.listBlocked(user.userId())
                .stream().map(BlockedUserResponse::from).toList());
    }

    @Operation(summary = "拉黑用户", description = "拉黑后对方无法给你发消息，也不会出现在你的搜索结果里。")
    @PostMapping("/{userId}")
    public ApiResponse<Void> block(@CurrentUser AuthenticatedUser user, @PathVariable long userId) {
        friendApplicationService.block(user.userId(), userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "解除拉黑", description = "恢复与对方的正常交互。")
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unblock(@CurrentUser AuthenticatedUser user, @PathVariable long userId) {
        friendApplicationService.unblock(user.userId(), userId);
        return ApiResponse.ok();
    }
}
