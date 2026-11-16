package com.veryal.access.http.controller;

import com.veryal.access.http.dto.ConversationCardResponse;
import com.veryal.access.http.dto.ConversationResponse;
import com.veryal.access.http.dto.CreateConversationRequest;
import com.veryal.access.http.dto.DeliveryResponse;
import com.veryal.access.http.dto.MessagePageResponse;
import com.veryal.access.http.dto.MessageResponse;
import com.veryal.access.http.dto.ReadReceiptResponse;
import com.veryal.access.http.dto.ReadRequest;
import com.veryal.access.http.dto.SendMessageRequest;
import com.veryal.access.http.dto.SendMessageResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.access.core.push.SendMessageNotifier;
import com.veryal.application.conversation.ConversationApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.application.message.SendMessageCommand;
import com.veryal.application.message.SendMessageResult;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.message.MessageType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 会话与消息接口。
 */
@Tag(name = "会话")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final MessageApplicationService messageApplicationService;
    private final SendMessageNotifier sendMessageNotifier;

    @Operation(summary = "创建或获取单聊会话", description = "同一对用户只会存在一个单聊会话，重复调用返回已存在的那个。返回 `conversationId`。")
    @PostMapping
    public ApiResponse<ConversationResponse> create(@CurrentUser AuthenticatedUser user,
                                                    @Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.ok(ConversationResponse.from(conversationApplicationService.createPrivate(
                user.userId(), request.peerUserId())));
    }

    @Operation(summary = "会话列表（含游标）", description = "返回每个会话的 `lastReadSeq` 与 `lastSeq`，客户端自行计算未读数。适合需要精细控制的场景。")
    @GetMapping
    public ApiResponse<List<ConversationResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(conversationApplicationService.list(user.userId()).stream()
                .map(ConversationResponse::from)
                .toList());
    }

    @Operation(summary = "会话列表（含展示信息）", description = "直接可渲染的列表：标题、成员数、最后一条消息预览、未读数，并已按最近活跃时间排序。前端列表用这个。")
    @GetMapping("/cards")
    public ApiResponse<List<ConversationCardResponse>> cards(
            @CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(conversationApplicationService.listCards(user.userId()).stream()
                .map(ConversationCardResponse::from)
                .toList());
    }

    @Operation(summary = "会话详情", description = "返回会话类型、成员 ID 列表与游标信息。")
    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> get(@CurrentUser AuthenticatedUser user,
                                                 @PathVariable String conversationId) {
        return ApiResponse.ok(ConversationResponse.from(
                conversationApplicationService.view(conversationId, user.userId())));
    }

    @Operation(summary = "发送消息", description = "`clientMessageId` 用于幂等：同一发送者重复提交相同 clientMessageId 只会落一条消息。`type` 支持 TEXT/EMOJI/IMAGE/AUDIO/VIDEO/FILE。文本内容会被敏感词过滤。")
    @PostMapping("/{conversationId}/messages")
    public ApiResponse<SendMessageResponse> send(@CurrentUser AuthenticatedUser user,
                                                 @PathVariable String conversationId,
                                                 @Valid @RequestBody SendMessageRequest request) {
        MessageType type = parseType(request.type());
        byte[] content = parseContent(request);
        SendMessageResult result = messageApplicationService.send(new SendMessageCommand(
                user.userId(), user.deviceId(), conversationId,
                request.clientMessageId(), type, content));
        sendMessageNotifier.notify(result);
        return ApiResponse.ok(SendMessageResponse.from(result));
    }

    @Operation(summary = "历史消息（游标分页）", description = "按 `cursor`（已确认的最大 seq）向后翻页，`limit` 建议 50~100。返回 `nextCursor` 与 `hasMore`。")
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<MessagePageResponse> history(@CurrentUser AuthenticatedUser user,
                                                    @PathVariable String conversationId,
                                                    @RequestParam(defaultValue = "0") long cursor,
                                                    @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(MessagePageResponse.from(messageApplicationService.history(
                user.userId(), conversationId, cursor, limit)));
    }

    @Operation(summary = "搜索会话内消息", description = "按关键词搜索该会话的历史消息，仅对文本消息生效。")
    @GetMapping("/{conversationId}/messages/search")
    public ApiResponse<MessagePageResponse> search(@CurrentUser AuthenticatedUser user,
                                                   @PathVariable String conversationId,
                                                   @RequestParam String keyword,
                                                   @RequestParam(defaultValue = "0") long cursor,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(MessagePageResponse.from(messageApplicationService.search(
                user.userId(), conversationId, keyword, cursor, limit)));
    }

    @Operation(summary = "上报已读", description = "推进读游标。游标只前进不后退，真正推进时会向对方广播已读回执。")
    @PostMapping("/{conversationId}/read")
    public ApiResponse<Void> read(@CurrentUser AuthenticatedUser user,
                                  @PathVariable String conversationId,
                                  @Valid @RequestBody ReadRequest request) {
        conversationApplicationService.markRead(user.userId(), conversationId,
                request.readSequence());
        return ApiResponse.ok();
    }

    @Operation(summary = "撤回消息", description = "仅发送者本人可在时间窗内撤回，撤回后消息保留占位并标记 `recalledAt`。")
    @PostMapping("/{conversationId}/messages/{messageId}/recall")
    public ApiResponse<MessageResponse> recall(@CurrentUser AuthenticatedUser user,
                                               @PathVariable String conversationId,
                                               @PathVariable long messageId) {
        return ApiResponse.ok(MessageResponse.from(messageApplicationService.recall(
                user.userId(), conversationId, messageId, user.deviceId())));
    }

    @Operation(summary = "查询已读回执", description = "返回哪些成员读到了哪个序号，用于群聊已读人数展示。")
    @GetMapping("/{conversationId}/read-receipts")
    public ApiResponse<List<ReadReceiptResponse>> readReceipts(@CurrentUser AuthenticatedUser user,
                                                               @PathVariable String conversationId) {
        return ApiResponse.ok(conversationApplicationService.readReceipts(user.userId(), conversationId).stream()
                .map(ReadReceiptResponse::from)
                .toList());
    }

    @Operation(summary = "查询投递状态", description = "按 `(messageId, deviceId)` 维度返回投递结果，用于排查消息是否到达某台设备。")
    @GetMapping("/{conversationId}/messages/{messageId}/deliveries")
    public ApiResponse<List<DeliveryResponse>> deliveries(@CurrentUser AuthenticatedUser user,
                                                          @PathVariable String conversationId,
                                                          @PathVariable long messageId) {
        return ApiResponse.ok(messageApplicationService.listDeliveries(user.userId(),
                        conversationId, messageId).stream()
                .map(DeliveryResponse::from)
                .toList());
    }

    private MessageType parseType(String type) {
        try {
            return MessageType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MESSAGE_TYPE, "unsupported message type: " + type);
        }
    }

    private byte[] parseContent(SendMessageRequest request) {
        if (request.contentBase64() != null && !request.contentBase64().isBlank()) {
            try {
                return Base64.getDecoder().decode(request.contentBase64());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid base64 content");
            }
        }
        if (request.content() != null) {
            return request.content().getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }
}
