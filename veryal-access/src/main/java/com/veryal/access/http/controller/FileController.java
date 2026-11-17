package com.veryal.access.http.controller;

import com.veryal.access.http.dto.FileResponse;
import com.veryal.access.http.dto.FileUrlResponse;
import com.veryal.access.http.dto.InitUploadRequest;
import com.veryal.access.http.dto.UploadSessionResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.access.http.support.ByteRange;
import com.veryal.application.file.ChunkedUploadService;
import com.veryal.application.file.FileApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
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
    /** 文件内容按 sha256 寻址、不可变，允许浏览器私有缓存一年。 */
    private static final String PRIVATE_CACHE = "private, max-age=31536000, immutable";

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

    @Operation(summary = "上传分片", description = "按分片序号幂等写入，重复上传同一分片直接覆盖，用于断点续传。")
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

    @Operation(summary = "下载文件内容",
            description = "以二进制流返回，支持 `Range` 断点续传与视频拖动；`disposition=inline` 可就地预览（仅限音视频、图片、PDF）。")
    @GetMapping("/{fileId}/content")
    public ResponseEntity<InputStreamResource> download(@CurrentUser AuthenticatedUser user,
                                                        @PathVariable long fileId,
                                                        @RequestParam(value = "disposition",
                                                                required = false) String disposition,
                                                        HttpServletRequest request) {
        FileApplicationService.FileStream content = fileApplicationService.open(
                user.userId(), fileId);
        String rawType = content.file().contentType();
        MediaType mediaType = mediaTypeOf(rawType);
        long size = Math.max(0, content.file().sizeBytes());
        boolean inline = "inline".equalsIgnoreCase(disposition) && inlineAllowed(rawType);

        HttpHeaders headers = new HttpHeaders();
        headers.setETag('"' + content.file().sha256() + '"');
        headers.setCacheControl(PRIVATE_CACHE);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentDisposition((inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(content.file().originalName(), StandardCharsets.UTF_8)
                .build());

        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        String etag = content.file().sha256();
        if (ifNoneMatch != null
                && (ifNoneMatch.contains('"' + etag + '"') || ifNoneMatch.contains(etag))) {
            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
        }

        ByteRange.Range range;
        try {
            range = ByteRange.parse(request.getHeader(HttpHeaders.RANGE), size);
        } catch (IllegalStateException e) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
            return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        headers.setContentType(mediaType);

        if (range == null) {
            headers.setContentLength(size);
            return new ResponseEntity<>(
                    new InputStreamResource(limit(content.open(0, 0), size)), headers,
                    HttpStatus.OK);
        }
        headers.setContentLength(range.length());
        headers.set(HttpHeaders.CONTENT_RANGE,
                "bytes " + range.start() + "-" + range.end() + "/" + size);
        return new ResponseEntity<>(
                new InputStreamResource(limit(content.open(range.start(), range.length()),
                        range.length())),
                headers, HttpStatus.PARTIAL_CONTENT);
    }

    @Operation(summary = "图片缩略图",
            description = "等比缩放到 `w` 像素内并转 JPEG，首次生成后落盘缓存。图片消息列表用它代替原图。")
    @GetMapping("/{fileId}/thumb")
    public ResponseEntity<byte[]> thumbnail(@CurrentUser AuthenticatedUser user,
                                            @PathVariable long fileId,
                                            @RequestParam(defaultValue = "480") int w,
                                            HttpServletRequest request) {
        FileApplicationService.Thumbnail thumb = fileApplicationService.thumbnail(
                user.userId(), fileId, w);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(thumb.contentType());
        } catch (RuntimeException e) {
            mediaType = MediaType.IMAGE_JPEG;
        }
        return binaryResponse(thumb.content(), mediaType, thumb.etag(), null, request);
    }

    /**
     * 包装成最多读 {@code length} 字节的流。
     *
     * <p>Spring 用 {@code StreamUtils.copy} 写响应，会一直读到 EOF；
     * 不限制的话 206 会把整个剩余文件都写出去。</p>
     */
    private static InputStream limit(InputStream input, long length) {
        return new FilterInputStream(input) {
            private long remaining = length;

            @Override
            public int read() throws IOException {
                if (remaining <= 0) {
                    return -1;
                }
                int value = super.read();
                if (value >= 0) {
                    remaining--;
                }
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int len) throws IOException {
                if (remaining <= 0) {
                    return -1;
                }
                int read = super.read(buffer, offset, (int) Math.min(len, remaining));
                if (read > 0) {
                    remaining -= read;
                }
                return read;
            }
        };
    }

    private static MediaType mediaTypeOf(String rawContentType) {
        try {
            return MediaType.parseMediaType(rawContentType);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 允许在同源就地打开的类型。
     *
     * <p>排除 SVG（XML 文档，可执行脚本）与 HTML/XML/JS：这些一旦在本站 origin 打开
     * 就能拿到 localStorage 里的 token。</p>
     */
    private static boolean inlineAllowed(String rawContentType) {
        String type = rawContentType == null ? "" : rawContentType.toLowerCase(Locale.ROOT);
        if (type.isBlank() || type.contains("svg")) {
            return false;
        }
        return type.startsWith("video/") || type.startsWith("audio/")
                || type.startsWith("image/") || type.equals("application/pdf");
    }

    /**
     * 二进制响应统一走这里（缩略图）：内容不可变，用 sha256 做 ETag 并允许浏览器长期缓存。
     */
    private ResponseEntity<byte[]> binaryResponse(byte[] body, MediaType mediaType, String etag,
                                                  String disposition, HttpServletRequest request) {
        String quoted = '"' + etag + '"';
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        boolean notModified = ifNoneMatch != null
                && (ifNoneMatch.contains(quoted) || ifNoneMatch.contains(etag));
        ResponseEntity.BodyBuilder builder = notModified
                ? ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                : ResponseEntity.ok().contentType(mediaType).contentLength(body.length);
        builder.eTag(quoted)
                .header(HttpHeaders.CACHE_CONTROL, PRIVATE_CACHE)
                .header("X-Content-Type-Options", "nosniff");
        if (disposition != null) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, disposition);
        }
        return notModified ? builder.build() : builder.body(body);
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
