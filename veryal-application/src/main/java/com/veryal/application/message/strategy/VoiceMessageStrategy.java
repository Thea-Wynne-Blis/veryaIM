package com.veryal.application.message.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

/**
 * 语音消息：{"fileId":123,"durationSeconds":12}。
 */
@Component
public class VoiceMessageStrategy extends AbstractFileMessageStrategy {

    public VoiceMessageStrategy(FileRepository fileRepository) {
        super(fileRepository);
    }

    @Override
    public MessageType type() {
        return MessageType.AUDIO;
    }

    @Override
    public void validate(MessageContentContext context) {
        JsonNode node = parseJson(context);
        requireOwnedFile(context, node);
        optionalPositive(node, "durationSeconds");
        if (node.path("durationSeconds").asLong(0) > 3600) {
            throw new com.veryal.common.exception.BusinessException(
                    com.veryal.common.exception.ErrorCode.MESSAGE_CONTENT_INVALID,
                    "durationSeconds must not exceed 3600");
        }
    }
}
