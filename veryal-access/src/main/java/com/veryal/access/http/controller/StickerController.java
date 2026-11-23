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
 * <p>搜索只返回第三方图片地址（前端直接展示，不必经服务端中转）；
 * 点选发送时再走 {@code /collect} 下载并落到本站存储，
 * 这样消息里引用的是本站 fileId，第三方站点挂掉或防盗链都不影响历史消息。</p>
 */
@Tag(name = "表情包")
@RestController
@RequestMapping("/api/v1/stickers")
@RequiredArgsConstructor
public class StickerController {

    private final StickerSource stickerSource;
    private final FileApplicationService fileApplicationService;

    @Operation(summary = "搜索网络表情包", description = "关键词必填，page 从 1 开始；结果按 10 分钟缓存。")
    @GetMapping("/search")
    public ApiResponse<StickerSearchResponse> search(@RequestParam String keyword,
                                                     @RequestParam(defaultValue = "1") int page) {
        int safePage = Math.max(1, Math.min(page, 20));
        return ApiResponse.ok(new StickerSearchResponse(keyword,
                safePage, stickerSource.search(keyword, safePage)));
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
