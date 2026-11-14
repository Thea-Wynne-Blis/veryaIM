package com.veryal.access.http.controller;

import com.veryal.access.http.dto.FileResponse;
import com.veryal.access.http.dto.FileUrlResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.file.FileApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 文件 API：上传、元数据、下载、删除与临时 URL。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final long MAX_URL_TTL_SECONDS = 7 * 24 * 60 * 60;

    private final FileApplicationService fileApplicationService;

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
        return ApiResponse.ok(FileResponse.from(fileApplicationService.upload(user.tenantId(),
                user.userId(), conversationId, file.getOriginalFilename(), file.getContentType(), content)));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<FileResponse> get(@CurrentUser AuthenticatedUser user,
                                         @PathVariable long fileId) {
        return ApiResponse.ok(FileResponse.from(
                fileApplicationService.get(user.tenantId(), user.userId(), fileId)));
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> download(@CurrentUser AuthenticatedUser user,
                                           @PathVariable long fileId) {
        FileApplicationService.FileContent content = fileApplicationService.download(
                user.tenantId(), user.userId(), fileId);
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

    @GetMapping("/{fileId}/url")
    public ApiResponse<FileUrlResponse> url(@CurrentUser AuthenticatedUser user,
                                            @PathVariable long fileId,
                                            @RequestParam(defaultValue = "600") long ttlSeconds) {
        long ttl = Math.max(1, Math.min(MAX_URL_TTL_SECONDS, ttlSeconds));
        String url = fileApplicationService.presignedUrl(user.tenantId(), user.userId(), fileId,
                Duration.ofSeconds(ttl));
        if (url == null) {
            return ApiResponse.ok(new FileUrlResponse("/api/v1/files/" + fileId + "/content", 0));
        }
        return ApiResponse.ok(new FileUrlResponse(url, ttl));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user,
                                    @PathVariable long fileId) {
        fileApplicationService.delete(user.tenantId(), user.userId(), fileId);
        return ApiResponse.ok();
    }
}
