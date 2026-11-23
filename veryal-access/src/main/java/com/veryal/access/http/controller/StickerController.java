package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CollectStickerRequest;
import com.veryal.access.http.dto.StickerFileResponse;
import com.veryal.access.http.dto.StickerSearchResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.access.http.sticker.StickerSource;
import com.veryal.application.file.FileApplicationService;
import com.veryal.application.file.FileView;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * 表情包接口：搜索网络表情 + 收藏为本站文件。
 *
 * <p>搜索结果里的预览图由 {@code /image} 同源中转：客户端直连第三方 CDN
 * 在部分网络下会长时间挂住，面板看起来像「一直在转圈」；中转后可以复用
 * 服务端的连接与预取缓存，也让第三方防盗链/跨域策略不再影响展示。</p>
 *
 * <p>点选发送时走 {@code /collect} 下载并落到本站存储，
 * 这样消息里引用的是本站 fileId，第三方站点挂掉或防盗链都不影响历史消息。</p>
 */
@Tag(name = "表情包")
@RestController
@RequestMapping("/api/v1/stickers")
@RequiredArgsConstructor
public class StickerController {

    private final StickerSource stickerSource;
    private final FileApplicationService fileApplicationService;

    @Operation(summary = "搜索网络表情包", description = "关键词必填，page 从 1 开始；结果缓存 30 分钟并后台刷新。")
    @GetMapping("/search")
    public ApiResponse<StickerSearchResponse> search(@RequestParam String keyword,
                                                     @RequestParam(defaultValue = "1") int page) {
        int safePage = Math.max(1, Math.min(page, 20));
        return ApiResponse.ok(new StickerSearchResponse(keyword,
                safePage, stickerSource.search(keyword, safePage)));
    }

    @Operation(summary = "表情包预览图",
            description = "公开接口；只中转白名单图源，结果缓存一天，避免客户端直连第三方 CDN。")
    @GetMapping("/image")
    public ResponseEntity<byte[]> image(@RequestParam("url") String url) {
        StickerSource.Download download = stickerSource.preview(url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.content().length);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, immutable");
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(download.content(), headers, HttpStatus.OK);
    }

    @Operation(summary = "收藏表情包并发送",
            description = "服务端下载图片、落库并返回 fileId；前端用该 fileId 发送 IMAGE 消息。")
    @PostMapping("/collect")
    public ApiResponse<StickerFileResponse> collect(@CurrentUser AuthenticatedUser user,
                                                    @Valid @RequestBody CollectStickerRequest request) {
        StickerSource.Download download = stickerSource.download(request.url());
        FileView file = fileApplicationService.store(user.userId(), request.conversationId(),
                download.filename(), download.contentType(), download.content());
        int[] size = dimensionsOf(download.content());
        return ApiResponse.ok(new StickerFileResponse(file.fileId(), file.name(),
                file.contentType(), file.sizeBytes(), size[0], size[1]));
    }

    /** 读图片头拿宽高；解不出来（例如 WebP 无插件）就返回 0，消息气泡按默认尺寸显示。 */
    private static int[] dimensionsOf(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image != null) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (Exception ignored) {
            // 尺寸只用于气泡占位，读不到不算失败。
        }
        return new int[]{0, 0};
    }
}
