package com.veryal.access.http.controller;

import com.veryal.access.http.dto.MessagePageResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.sync.SyncApplicationService;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 离线消息同步接口。使用游标而不是 OFFSET。
 */
@Tag(name = "消息同步")
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncApplicationService syncApplicationService;

    @Operation(summary = "按游标补拉消息", description = "客户端上线或断线重连时调用：传入本地已有的最大 `cursor`，返回其后的全部消息。与 WebSocket 推送配合实现不丢不重。")
    @GetMapping("/messages")
    public ApiResponse<MessagePageResponse> messages(@CurrentUser AuthenticatedUser user,
                                                     @RequestParam String conversationId,
                                                     @RequestParam(defaultValue = "0") long cursor,
                                                     @RequestParam(defaultValue = "100") int limit) {
        if (conversationId == null || conversationId.isBlank() || conversationId.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid conversationId");
        }
        return ApiResponse.ok(MessagePageResponse.from(syncApplicationService.syncMessages(
                user.userId(), conversationId, cursor, limit)));
    }
}
