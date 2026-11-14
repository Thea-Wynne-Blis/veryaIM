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
        long fileId = node.path("fileId").asLong(0);
        if (fileId <= 0) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID, "fileId is required");
        }
        StoredFile file = fileRepository.findById(context.tenantId(), fileId)
                .filter(item -> item.status() == FileStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND,
                        "file not found: " + fileId));
        if (file.uploaderId() != context.senderId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "file does not belong to sender");
        }
        return file;
    }

    protected void optionalPositive(JsonNode node, String field) {
        if (node.has(field) && node.path(field).asLong(0) <= 0) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID,
                    field + " must be positive");
        }
    }
}
