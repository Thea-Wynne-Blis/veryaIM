package com.veryal.application.message.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.StoredFile;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

/**
 * 图片消息：{"fileId":123,"width":1080,"height":1920}。
 */
@Component
public class ImageMessageStrategy extends AbstractFileMessageStrategy {

    public ImageMessageStrategy(FileRepository fileRepository) {
        super(fileRepository);
    }

    @Override
    public MessageType type() {
        return MessageType.IMAGE;
    }

    @Override
    public void validate(MessageContentContext context) {
        JsonNode node = parseJson(context);
        StoredFile file = requireOwnedFile(context, node);
        requireContentType(file, "image/");
        String contentType = file.contentType() == null
                ? "" : file.contentType().toLowerCase(java.util.Locale.ROOT);
        if (contentType.contains("svg")) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                    "svg is not allowed as image message");
        }
        optionalPositive(node, "width");
        optionalPositive(node, "height");
    }
}
