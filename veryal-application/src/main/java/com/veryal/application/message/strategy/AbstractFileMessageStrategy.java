package com.veryal.application.message.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.FileStatus;
import com.veryal.domain.file.StoredFile;
import lombok.RequiredArgsConstructor;

/**
 * 文件类消息（图片/语音/视频/文件）的公共校验：必须引用已上传且属于发送者的文件。
 */
@RequiredArgsConstructor
public abstract class AbstractFileMessageStrategy implements MessageTypeStrategy {

    protected final FileRepository fileRepository;

    protected JsonNode parseJson(MessageContentContext context) {
        try {
            JsonNode node = JsonUtils.mapper().readTree(context.content());
            if (node == null || !node.isObject()) {
                throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                        type().name().toLowerCase() + " payload must be a JSON object");
            }
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                    type().name().toLowerCase() + " payload is not valid JSON");
        }
    }

    protected StoredFile requireOwnedFile(MessageContentContext context, JsonNode node) {
        return requireFile(context, node.path("fileId").asLong(0));
    }

    protected StoredFile requireFile(MessageContentContext context, long fileId) {
        if (fileId <= 0) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "fileId is required");
        }
        StoredFile file = fileRepository.findById(fileId)
                .filter(item -> item.status() == FileStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND,
                        "file not found: " + fileId));
        if (file.uploaderId() != context.senderId()) {
            if (file.conversationId() == null
                    || !file.conversationId().equals(context.conversationId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "file does not belong to sender");
            }
        }
        return file;
    }

    protected void optionalPositive(JsonNode node, String field) {
        if (node.has(field) && node.path(field).asLong(0) <= 0) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                    field + " must be positive");
        }
    }

    /**
     * 校验文件内容类型。图片/视频/语音消息必须引用对应大类的文件，
     * 否则可以把任意可执行内容伪装成图片塞进会话（前端会按类型渲染）。
     */
    protected void requireContentType(StoredFile file, String... allowedPrefixes) {
        String contentType = file.contentType() == null
                ? "" : file.contentType().toLowerCase(java.util.Locale.ROOT);
        for (String prefix : allowedPrefixes) {
            if (contentType.startsWith(prefix)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                "file content type " + contentType + " is not allowed for "
                        + type().name().toLowerCase(java.util.Locale.ROOT) + " message");
    }
}
