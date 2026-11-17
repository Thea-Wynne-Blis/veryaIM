package com.veryal.application.message.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.StoredFile;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

/**
 * 语音消息：{"fileId":123,"durationSeconds":12}。
 *
 * <p>浏览器的 MediaRecorder 录音常见容器是 {@code audio/webm}，部分客户端会标成
 * {@code video/webm}（只有音轨），所以两者都接受。</p>
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
        StoredFile file = requireOwnedFile(context, node);
        requireContentType(file, "audio/", "video/webm", "video/ogg");
        optionalPositive(node, "durationSeconds");
        if (node.path("durationSeconds").asLong(0) > 3600) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                    "durationSeconds must not exceed 3600");
        }
    }
}
