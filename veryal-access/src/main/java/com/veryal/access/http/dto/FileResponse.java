package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.file.FileView;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 文件上传/查询响应。
 */
public record FileResponse(
                                 @Schema(description = "文件 ID")
                                 long fileId,
                                 @Schema(description = "名称")
                                 String name,
                                 String contentType,
                                 long sizeBytes,
                                 String sha256,
                                 String storageType,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt
) {

    public static FileResponse from(FileView view) {
        return new FileResponse(view.fileId(), view.name(), view.contentType(), view.sizeBytes(),
                view.sha256(), view.storageType(), view.createdAt());
    }
}
