package com.veryal.access.http.support;

import com.veryal.application.file.FileApplicationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文件二进制响应装配：支持 Range、ETag 与浏览器长期缓存。
 *
 * <p>文件 API 与朋友圈媒体共用同一套语义，避免两处各写一遍 Range / 缓存逻辑。</p>
 */
public final class FileResponses {

    /** 文件内容按 sha256 寻址、不可变，允许浏览器私有缓存一年。 */
    private static final String PRIVATE_CACHE = "private, max-age=31536000, immutable";

    private FileResponses() {
    }

    public static ResponseEntity<InputStreamResource> stream(
            FileApplicationService.FileStream content, String disposition,
            HttpServletRequest request) {
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

    public static ResponseEntity<byte[]> image(FileApplicationService.Thumbnail thumb,
                                               HttpServletRequest request) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(thumb.contentType());
        } catch (RuntimeException e) {
            mediaType = MediaType.IMAGE_JPEG;
        }
        byte[] body = thumb.content();
        String quoted = '"' + thumb.etag() + '"';
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        boolean notModified = ifNoneMatch != null
                && (ifNoneMatch.contains(quoted) || ifNoneMatch.contains(thumb.etag()));
        ResponseEntity.BodyBuilder builder = notModified
                ? ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                : ResponseEntity.ok().contentType(mediaType).contentLength(body.length);
        builder.eTag(quoted)
                .header(HttpHeaders.CACHE_CONTROL, PRIVATE_CACHE)
                .header("X-Content-Type-Options", "nosniff");
        return notModified ? builder.build() : builder.body(body);
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
}
