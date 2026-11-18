package com.veryal.access.http.controller;

import com.veryal.access.http.dto.ConversationSearchResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.search.ConversationSearchService;
import com.veryal.common.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@Tag(name = "搜索")
@RestController
@RequiredArgsConstructor
public class ConversationSearchController {
    private final ConversationSearchService searchService;

    @Operation(summary = "查找聊天内容", description = "统一检索文本、文件、图片与视频、链接、语音；支持发送者和时间筛选，空关键词浏览历史，按 seq 倒序分页。")
    @GetMapping("/api/v1/conversations/{conversationId}/search")
    public ApiResponse<ConversationSearchResponse> search(@CurrentUser AuthenticatedUser user,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(required = false) Long senderId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(ConversationSearchResponse.from(searchService.search(user.userId(),
                conversationId, keyword, category, senderId, from, to, cursor, limit)));
    }
}
