package com.veryal.access.http.controller;

import com.veryal.access.http.dto.FileResponse;
import com.veryal.access.http.dto.FileUrlResponse;
import com.veryal.access.http.dto.InitUploadRequest;
import com.veryal.access.http.dto.UploadSessionResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.file.ChunkedUploadService;
import com.veryal.application.file.FileApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 文件 API：上传、分片上传、元数据、下载、删除与临时 URL。
 */
@Tag(name = "文件")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final long MAX_URL_TTL_SECONDS = 7 * 24 * 60 * 60;

    private final FileApplicationService fileApplicationService;
    private final ChunkedUploadService chunkedUploadService;

    @Operation(summary = "直接上传文件", description = "小文件中转：一次性提交文件内容，返回 `fileId`。大文件请走分片接口。")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileResponse> upload(@CurrentUser AuthenticatedUser user,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "conversationId", required = false)
                                            String conversationId) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "failed to read uploaded file");
        }
        return ApiResponse.ok(FileResponse.from(fileApplicationService.upload(user.userId(), conversationId, file.getOriginalFilename(), file.getContentType(), content)));
    }

    @Operation(summary = "我的文件", description = "列出我上传过的文件，按时间倒序，供文件中心页面展示。")
    @GetMapping("/mine")
    public ApiResponse<java.util.List<FileResponse>> mine(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(fileApplicationService.listMine(user.userId(), 100).stream()
                .map(FileResponse::from)
                .toList());
    }

    @Operation(summary = "按 ID 查询文件元信息", description = "返回文件名、大小、类型与创建时间。")
    @GetMapping("/{fileId}")
    public ApiResponse<FileResponse> get(@CurrentUser AuthenticatedUser user,
                                         @PathVariable long fileId) {
        return ApiResponse.ok(FileResponse.from(
                fileApplicationService.get(user.userId(), fileId)));
    }

    @Operation(summary = "创建分片上传", description = "声明文件名、大小与分片大小，返回 `uploadId` 与已上传分片列表，用于断点续传。")
    @PostMapping("/uploads")
    public ApiResponse<UploadSessionResponse> initUpload(@CurrentUser AuthenticatedUser user,
                                                         @Valid @RequestBody InitUploadRequest request) {
        return ApiResponse.ok(UploadSessionResponse.from(chunkedUploadService.init(user.userId(), request.filename(), request.contentType(), request.sizeBytes(),
                request.conversationId())));
    }

    @Operation(summary = "上传并替换", description = "按 fileId 覆盖已有文件内容。")
    @PutMapping(value = "/uploads/{uploadId}/chunks/{index}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<UploadSessionResponse> uploadChunk(@CurrentUser AuthenticatedUser user,
                                                          @PathVariable long uploadId,
                                                          @PathVariable int index,
                                                          @RequestBody byte[] chunk) {
        return ApiResponse.ok(UploadSessionResponse.from(chunkedUploadService.uploadChunk(
                user.userId(), uploadId, index, chunk)));
    }

    @Operation(summary = "查询上传进度", description = "返回已接收的分片编号，客户端据此只补传缺失分片。")
    @GetMapping("/uploads/{uploadId}")
    public ApiResponse<UploadSessionResponse> uploadStatus(@CurrentUser AuthenticatedUser user,
                                                           @PathVariable long uploadId) {
        return ApiResponse.ok(UploadSessionResponse.from(chunkedUploadService.status(
                user.userId(), uploadId)));
    }

    @Operation(summary = "合并分片", description = "全部分片到齐后合并为最终文件，返回 `fileId`。")
    @PostMapping("/uploads/{uploadId}/complete")
    public ApiResponse<FileResponse> completeUpload(@CurrentUser AuthenticatedUser user,
                                                    @PathVariable long uploadId) {
        return ApiResponse.ok(FileResponse.from(chunkedUploadService.complete(
                user.userId(), uploadId)));
    }

    @Operation(summary = "取消分片上传", description = "清理暂存分片。")
    @DeleteMapping("/uploads/{uploadId}")
    public ApiResponse<Void> cancelUpload(@CurrentUser AuthenticatedUser user,
                                          @PathVariable long uploadId) {
        chunkedUploadService.cancel(user.userId(), uploadId);
        return ApiResponse.ok();
    }

    @Operation(summary = "下载文件内容", description = "以二进制流返回，私聊文件需具备会话成员身份。")
    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> download(@CurrentUser AuthenticatedUser user,
                                           @PathVariable long fileId) {
        FileApplicationService.FileContent content = fileApplicationService.download(
                user.userId(), fileId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.file().contentType());
        } catch (RuntimeException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.file().originalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(content.content());
    }

    @Operation(summary = "获取下载地址", description = "返回可直接访问的 URL（本地存储为相对路径，对象存储为签名地址）。")
    @GetMapping("/{fileId}/url")
    public ApiResponse<FileUrlResponse> url(@CurrentUser AuthenticatedUser user,
                                            @PathVariable long fileId,
                                            @RequestParam(defaultValue = "600") long ttlSeconds) {
        long ttl = Math.max(1, Math.min(MAX_URL_TTL_SECONDS, ttlSeconds));
        String url = fileApplicationService.presignedUrl(user.userId(), fileId,
                Duration.ofSeconds(ttl));
        if (url == null) {
            return ApiResponse.ok(new FileUrlResponse("/api/v1/files/" + fileId + "/content", 0));
        }
        return ApiResponse.ok(new FileUrlResponse(url, ttl));
    }

    @Operation(summary = "删除文件", description = "仅上传者本人可删除。")
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user,
                                    @PathVariable long fileId) {
        fileApplicationService.delete(user.userId(), fileId);
        return ApiResponse.ok();
    }
}
