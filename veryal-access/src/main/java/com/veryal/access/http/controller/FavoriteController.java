package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateFavoriteRequest;
import com.veryal.access.http.dto.FavoriteResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.favorite.FavoriteApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 收藏接口：收藏消息、列表（可按关键词过滤）、取消收藏。
 */
@Tag(name = "收藏")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteApplicationService favoriteApplicationService;

    @Operation(summary = "收藏消息", description = "重复收藏同一条消息是幂等的，返回已有收藏。")
    @PostMapping
    public ApiResponse<FavoriteResponse> add(@CurrentUser AuthenticatedUser user,
                                             @Valid @RequestBody CreateFavoriteRequest request) {
        return ApiResponse.ok(FavoriteResponse.from(favoriteApplicationService.add(
                user.userId(), request.conversationId(), request.messageId())));
    }

    @Operation(summary = "收藏列表", description = "按收藏时间倒序；keyword 可选，过滤文件名 / 来源会话 / 发送者 / 文本。")
    @GetMapping
    public ApiResponse<List<FavoriteResponse>> list(@CurrentUser AuthenticatedUser user,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(favoriteApplicationService.list(user.userId(), keyword, limit).stream()
                .map(FavoriteResponse::from)
                .toList());
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{favoriteId}")
    public ApiResponse<Void> remove(@CurrentUser AuthenticatedUser user,
                                    @PathVariable long favoriteId) {
        favoriteApplicationService.remove(user.userId(), favoriteId);
        return ApiResponse.ok();
    }
}
