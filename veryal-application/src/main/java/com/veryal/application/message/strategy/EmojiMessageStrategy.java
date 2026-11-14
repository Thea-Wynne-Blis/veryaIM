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
 * 表情消息：UTF-8 表情文本或短代码，限制 4KB。
 */
@Component
public class EmojiMessageStrategy implements MessageTypeStrategy {

    private static final int MAX_BYTES = 4 * 1024;

    @Override
    public MessageType type() {
        return MessageType.EMOJI;
    }

    @Override
    public void validate(MessageContentContext context) {
        if (context.content().length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "emoji payload is too large");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(context.content()))
                    .toString();
            if (text.isBlank()) {
                throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "emoji must not be blank");
            }
        } catch (CharacterCodingException e) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "emoji is not valid UTF-8");
        }
    }
}
