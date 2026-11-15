package com.veryal.access.http.controller;

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

/**
 * 会话与消息接口。
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final MessageApplicationService messageApplicationService;

    @PostMapping
    public ApiResponse<ConversationResponse> create(@CurrentUser AuthenticatedUser user,
                                                    @Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.ok(ConversationResponse.from(conversationApplicationService.createPrivate(
                user.tenantId(), user.userId(), request.peerUserId())));
    }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(conversationApplicationService.list(user.tenantId(), user.userId()).stream()
                .map(ConversationResponse::from)
                .toList());
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> get(@CurrentUser AuthenticatedUser user,
                                                 @PathVariable String conversationId) {
        return ApiResponse.ok(ConversationResponse.from(
                conversationApplicationService.view(conversationId, user.tenantId(), user.userId())));
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<SendMessageResponse> send(@CurrentUser AuthenticatedUser user,
                                                 @PathVariable String conversationId,
                                                 @Valid @RequestBody SendMessageRequest request) {
        MessageType type = parseType(request.type());
        byte[] content = parseContent(request);
        SendMessageResult result = messageApplicationService.send(new SendMessageCommand(
                user.tenantId(), user.userId(), user.deviceId(), conversationId,
                request.clientMessageId(), type, content));
        return ApiResponse.ok(SendMessageResponse.from(result));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<MessagePageResponse> history(@CurrentUser AuthenticatedUser user,
                                                    @PathVariable String conversationId,
                                                    @RequestParam(defaultValue = "0") long cursor,
                                                    @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(MessagePageResponse.from(messageApplicationService.history(
                user.tenantId(), user.userId(), conversationId, cursor, limit)));
    }

    @GetMapping("/{conversationId}/messages/search")
    public ApiResponse<MessagePageResponse> search(@CurrentUser AuthenticatedUser user,
                                                   @PathVariable String conversationId,
                                                   @RequestParam String keyword,
                                                   @RequestParam(defaultValue = "0") long cursor,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(MessagePageResponse.from(messageApplicationService.search(
                user.tenantId(), user.userId(), conversationId, keyword, cursor, limit)));
    }

    @PostMapping("/{conversationId}/read")
    public ApiResponse<Void> read(@CurrentUser AuthenticatedUser user,
                                  @PathVariable String conversationId,
                                  @Valid @RequestBody ReadRequest request) {
        conversationApplicationService.markRead(user.tenantId(), user.userId(), conversationId,
                request.readSequence());
        return ApiResponse.ok();
    }

    @PostMapping("/{conversationId}/messages/{messageId}/recall")
    public ApiResponse<MessageResponse> recall(@CurrentUser AuthenticatedUser user,
                                               @PathVariable String conversationId,
                                               @PathVariable long messageId) {
        return ApiResponse.ok(MessageResponse.from(messageApplicationService.recall(
                user.tenantId(), user.userId(), conversationId, messageId)));
    }

    @GetMapping("/{conversationId}/read-receipts")
    public ApiResponse<List<ReadReceiptResponse>> readReceipts(@CurrentUser AuthenticatedUser user,
                                                               @PathVariable String conversationId) {
        return ApiResponse.ok(conversationApplicationService.readReceipts(user.tenantId(),
                        user.userId(), conversationId).stream()
                .map(ReadReceiptResponse::from)
                .toList());
    }

    @GetMapping("/{conversationId}/messages/{messageId}/deliveries")
    public ApiResponse<List<DeliveryResponse>> deliveries(@CurrentUser AuthenticatedUser user,
                                                          @PathVariable String conversationId,
                                                          @PathVariable long messageId) {
        return ApiResponse.ok(messageApplicationService.listDeliveries(user.tenantId(), user.userId(),
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
