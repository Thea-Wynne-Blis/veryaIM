package com.veryal.application.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
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
 * <p>解码失败或压缩后反而变大时保留原图，避免破坏客户端上传。</p>
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
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return content;
            }
            int maxSide = Math.max(source.getWidth(), source.getHeight());
            double scale = maxSide > MAX_DIMENSION ? (double) MAX_DIMENSION / maxSide : 1.0;
            boolean needsResize = scale < 1.0;
            if (!needsResize && content.length <= RECOMPRESS_THRESHOLD) {
                return content;
            }
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
