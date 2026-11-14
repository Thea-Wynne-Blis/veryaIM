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

/**
 * 离线消息同步接口。使用游标而不是 OFFSET。
 */
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncApplicationService syncApplicationService;

    @GetMapping("/messages")
    public ApiResponse<MessagePageResponse> messages(@CurrentUser AuthenticatedUser user,
                                                     @RequestParam String conversationId,
                                                     @RequestParam(defaultValue = "0") long cursor,
                                                     @RequestParam(defaultValue = "100") int limit) {
        if (conversationId == null || conversationId.isBlank() || conversationId.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid conversationId");
        }
        return ApiResponse.ok(MessagePageResponse.from(syncApplicationService.syncMessages(
                user.tenantId(), user.userId(), conversationId, cursor, limit)));
    }
}
