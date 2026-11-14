package com.veryal.application.message.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.domain.file.FileRepository;
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
        requireOwnedFile(context, node);
        optionalPositive(node, "width");
        optionalPositive(node, "height");
    }
}
