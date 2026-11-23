package com.veryal.application.file;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片上传的公共校验。
 *
 * <p>头像与聊天背景都需要「限制体积、限制像素、只认真实字节能解码的图片」，
 * 这里抽成一处，避免两份几乎相同的 ImageIO 代码各自漂移。SVG 一律拒绝：
 * 它是可以携带脚本的 XML，不能当普通图片回传。</p>
 */
public final class ImageContentValidator {

    private ImageContentValidator() {
    }

    /**
     * 校验并返回归一化后的 content type。
     *
     * @param maxBytes  上传体积上限
     * @param maxPixels 解码前的像素上限，防止小体积的「像素炸弹」
     * @param allowed   允许的 content type，例如 image/jpeg、image/png
     */
    public static String validate(String contentType, byte[] content, long maxBytes,
                                  long maxPixels, Set<String> allowed) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "图片内容不能为空");
        }
        if (content.length > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "图片不能超过 " + Math.max(1, maxBytes / 1024 / 1024) + "MB");
        }
        String type = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "仅支持 " + describe(allowed) + " 格式的图片");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = input == null
                    ? Collections.emptyIterator() : ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "图片无法识别");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > maxPixels) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "图片尺寸过大");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "图片无法识别");
        }
        return type;
    }

    /** 扩展名随 content type 决定，文件名本身不可信。 */
    public static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private static String describe(Set<String> allowed) {
        return allowed.stream()
                .map(type -> type.substring(type.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining("、"));
    }
}
