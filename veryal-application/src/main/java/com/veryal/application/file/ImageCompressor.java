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
 * 图片压缩：JPEG/PNG 超过 1920px 时等比缩放；大图重编码为 JPEG（质量 0.85）。
 *
 * <p>只有真的需要缩小或重编码时才完整解码。仅为了读宽高时走 ImageReader 头信息，
 * 否则一张 2.4MB 的 1920×1080 PNG 光解码就要 200ms 以上，上传会明显变慢。</p>
 */
@Slf4j
@Component
public class ImageCompressor {

    static final int MAX_DIMENSION = 1920;
    static final long RECOMPRESS_THRESHOLD = 1024L * 1024;

    public byte[] compress(byte[] content, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!type.startsWith("image/jpeg") && !type.startsWith("image/png")) {
            return content;
        }
        int[] size = readDimensions(content);
        if (size == null) {
            return content;
        }
        int maxSide = Math.max(size[0], size[1]);
        boolean needsResize = maxSide > MAX_DIMENSION;
        if (!needsResize
                && (type.startsWith("image/png") || content.length <= RECOMPRESS_THRESHOLD)) {
            // 未超尺寸时 PNG 重编码几乎不会变小（截图类图片常常更大），
            // 却要付出一次完整解码 + 编码的 CPU，直接原图入库。
            return content;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return content;
            }
            double scale = needsResize ? (double) MAX_DIMENSION / maxSide : 1.0;
            BufferedImage target = needsResize
                    ? resize(source, (int) Math.round(source.getWidth() * scale),
                    (int) Math.round(source.getHeight() * scale))
                    : source;
            byte[] compressed = type.startsWith("image/png") ? encodePng(target) : encodeJpeg(target);
            return compressed.length < content.length || needsResize ? compressed : content;
        } catch (Exception e) {
            log.debug("image compression skipped: type={}, reason={}", contentType, e.getMessage());
            return content;
        }
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
