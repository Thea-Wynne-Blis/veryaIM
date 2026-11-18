package com.veryal.access.http.controller;

import com.veryal.access.http.dto.SearchResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.search.SearchApplicationService;
import com.veryal.common.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 全局搜索：一次返回联系人、群聊、聊天记录与文件。
 */
@Tag(name = "搜索")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchApplicationService searchApplicationService;

    @Operation(summary = "全局搜索", description = "只搜当前用户的好友、我加入的群、我参与的会话与文件。")
    @GetMapping
    public ApiResponse<SearchResponse> search(@CurrentUser AuthenticatedUser user,
                                              @RequestParam String keyword) {
        return ApiResponse.ok(SearchResponse.from(
                searchApplicationService.search(user.userId(), keyword)));
    }
}
