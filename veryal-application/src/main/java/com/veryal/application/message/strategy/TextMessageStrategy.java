package com.veryal.application.message.strategy;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 文本消息：严格 UTF-8，非空。
 */
@Component
public class TextMessageStrategy implements MessageTypeStrategy {

    private static final int MAX_BYTES = 64 * 1024;

    @Override
    public MessageType type() {
        return MessageType.TEXT;
    }

    @Override
    public void validate(MessageContentContext context) {
        String text = decodeUtf8(context.content());
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "text must not be blank");
        }
        if (context.content().length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "text is too large");
        }
    }

    protected String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "content is not valid UTF-8");
        }
    }
}
