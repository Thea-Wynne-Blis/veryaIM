package com.veryal.application.message.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 表情消息：UTF-8 表情文本，或 {"stickerId":"emoji_01"} 贴纸；限制 4KB。
 */
@Component
public class EmojiMessageStrategy implements MessageTypeStrategy {

    private static final int MAX_BYTES = 4 * 1024;
    private static final int MAX_STICKER_ID = 64;

    @Override
    public MessageType type() {
        return MessageType.EMOJI;
    }

    @Override
    public void validate(MessageContentContext context) {
        if (context.content().length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "emoji payload is too large");
        }
        String text = decode(context);
        if (text.trim().startsWith("{")) {
            validateSticker(text);
            return;
        }
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "emoji must not be blank");
        }
    }

    private void validateSticker(String text) {
        JsonNode node;
        try {
            node = JsonUtils.mapper().readTree(text);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                    "sticker payload is not valid JSON");
        }
        String stickerId = node.path("stickerId").asText("").trim();
        if (stickerId.isBlank() || stickerId.length() > MAX_STICKER_ID
                || !stickerId.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                    "stickerId must match [A-Za-z0-9._:-]{1,64}");
        }
    }

    private String decode(MessageContentContext context) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(context.content()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "emoji is not valid UTF-8");
        }
    }
}
