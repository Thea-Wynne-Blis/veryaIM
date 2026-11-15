package com.veryal.application.message.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

/**
 * 视频消息：{"fileId":123,"durationSeconds":30,"width":1080,"height":1920,"thumbnailFileId":456}。
 */
@Component
public class VideoMessageStrategy extends AbstractFileMessageStrategy {

    public VideoMessageStrategy(FileRepository fileRepository) {
        super(fileRepository);
    }

    @Override
    public MessageType type() {
        return MessageType.VIDEO;
    }

    @Override
    public void validate(MessageContentContext context) {
        JsonNode node = parseJson(context);
        requireOwnedFile(context, node);
        optionalPositive(node, "durationSeconds");
        optionalPositive(node, "width");
        optionalPositive(node, "height");
        optionalPositive(node, "coverFileId");
        optionalPositive(node, "thumbnailFileId");
        long coverFileId = node.path("coverFileId").asLong(0);
        if (coverFileId > 0) {
            requireFile(context, coverFileId);
        }
        long thumbnailFileId = node.path("thumbnailFileId").asLong(0);
        if (thumbnailFileId > 0) {
            requireFile(context, thumbnailFileId);
        }
    }
}
