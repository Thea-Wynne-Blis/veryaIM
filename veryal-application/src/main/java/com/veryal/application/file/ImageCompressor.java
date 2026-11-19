package com.veryal.application.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 图片压缩：JPEG/PNG 超过 1920px 时等比缩放，未超尺寸的原样入库。
 *
 * <p>只有真的需要缩小时才完整解码。仅为了读宽高时走 ImageReader 头信息，
 * 否则一张 2.4MB 的 1920×1080 PNG 光解码就要 200ms 以上，上传会明显变慢。</p>
 *
 * <p>尺寸内的图片不再重编码：单核解码 + 编码一次 1200 万像素要 1~3 秒，
 * 而客户端上传前已按同样的长边压过，服务端重复计算只会拖慢上传并挤占 CPU。</p>
 */
@Slf4j
@Component
public class ImageCompressor {

    static final int MAX_DIMENSION = 1920;

    public byte[] compress(byte[] content, String contentType) {
        if (!supportsCompression(contentType)) {
            return content;
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        int[] size = readDimensions(content);
        if (size == null) {
            return content;
        }
        int maxSide = Math.max(size[0], size[1]);
        if (maxSide <= MAX_DIMENSION) {
            // 已在展示尺寸内：直接原图入库，省掉一次完整解码 + 重编码。
            return content;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return content;
            }
            double scale = (double) MAX_DIMENSION / maxSide;
            BufferedImage target = resize(source,
                    (int) Math.round(source.getWidth() * scale),
                    (int) Math.round(source.getHeight() * scale));
            return type.startsWith("image/png") ? encodePng(target) : encodeJpeg(target);
        } catch (Exception e) {
            log.debug("image compression skipped: type={}, reason={}", contentType, e.getMessage());
            return content;
        }
    }

    /** 只有 JPEG/PNG 会被重新编码；WebP/GIF/AVIF 保持原始字节（动图也不能重编码）。 */
    public boolean supportsCompression(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        return type.startsWith("image/jpeg") || type.startsWith("image/png");
    }

    /**
     * 生成缩略图（等比缩放到 maxSide 内，统一 JPEG）。
     *
     * <p>聊天列表里图片气泡只有 258px 宽，却要下载 2MB 原图，
     * 所以进入会话时先取缩略图，点击看大图时再取原图。</p>
     *
     * <p>解码时用 {@code setSourceSubsampling} 直接按 1/4 采样，避免先解出 1920×1080
     * 再缩到 480，冷启动一条缩略图从 500ms+ 降到几十毫秒。</p>
     *
     * @return 缩略图字节；解码失败返回 null，由调用方回退原图
     */
    public byte[] thumbnail(byte[] content, int maxSide) {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                int maxCurrent = Math.max(sourceWidth, sourceHeight);
                if (maxCurrent <= 0) {
                    return null;
                }
                int step = Math.max(1, maxCurrent / Math.max(1, maxSide));
                javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                BufferedImage source = reader.read(0, param);
                if (source == null) {
                    return null;
                }
                int decodedMax = Math.max(source.getWidth(), source.getHeight());
                double scale = decodedMax > maxSide ? (double) maxSide / decodedMax : 1.0;
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                BufferedImage target = scale < 1.0 ? resize(source, width, height) : source;
                return encodeJpeg(target);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            log.debug("thumbnail generation failed, reason={}", e.getMessage());
            return null;
        }
    }

    /** 只读图片头信息拿宽高，不完整解码。失败返回 null。 */
    private int[] readDimensions(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, imageType);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encodeJpeg(BufferedImage source) throws Exception {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            graphics.dispose();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return writeDefault(rgb, "jpg");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.85f);
            }
            writer.write(null, new IIOImage(rgb, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private byte[] encodePng(BufferedImage source) throws Exception {
        return writeDefault(source, "png");
    }

    private byte[] writeDefault(BufferedImage source, String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, format, output);
        return output.toByteArray();
    }
}
