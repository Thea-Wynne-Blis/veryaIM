package com.veryal.application.message.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

/**
 * 文件消息：{"fileId":123,"fileName":"report.pdf"}。
 */
@Component
public class FileMessageStrategy extends AbstractFileMessageStrategy {

    public FileMessageStrategy(FileRepository fileRepository) {
        super(fileRepository);
    }

    @Override
    public MessageType type() {
        return MessageType.FILE;
    }

    @Override
    public void validate(MessageContentContext context) {
        JsonNode node = parseJson(context);
        requireOwnedFile(context, node);
        if (node.has("fileName") && node.path("fileName").asText("").length() > 255) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "fileName is too long");
        }
        optionalPositive(node, "sizeBytes");
    }
}
