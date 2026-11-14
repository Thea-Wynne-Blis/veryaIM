package com.veryal.domain.message;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;

/**
 * 消息内容约束。应用层在写入前统一校验。
 */
public final class MessagePolicy {

    public static final int MAX_TEXT_BYTES = 64 * 1024;
    public static final int MAX_CONTENT_BYTES = 256 * 1024;
    public static final int MAX_CLIENT_MESSAGE_ID_LENGTH = 64;
    public static final int MAX_CONVERSATION_ID_LENGTH = 64;

    private MessagePolicy() {
    }

    public static void validate(MessageType type, byte[] content) {
        if (type == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MESSAGE_TYPE, "message type must not be null");
        }
        if (!type.userSendable()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MESSAGE_TYPE,
                    "message type is reserved for server: " + type);
        }
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "message content must not be empty");
        }
        int limit = type == MessageType.TEXT ? MAX_TEXT_BYTES : MAX_CONTENT_BYTES;
        if (content.length > limit) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                    "message content too large: " + content.length + " > " + limit);
        }
    }

    public static void validateClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()
                || clientMessageId.length() > MAX_CLIENT_MESSAGE_ID_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid client message id");
        }
    }

    public static void validateConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()
                || conversationId.length() > MAX_CONVERSATION_ID_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid conversation id");
        }
    }
}
