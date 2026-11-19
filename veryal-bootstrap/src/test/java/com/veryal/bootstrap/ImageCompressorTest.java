package com.veryal.bootstrap;

import com.veryal.application.file.ImageCompressor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCompressorTest {

    @Test
    void resizesLargeJpeg() throws Exception {
        BufferedImage image = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, 3000, 1000);
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, 1500, 500);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", original);

        byte[] compressed = new ImageCompressor().compress(original.toByteArray(), "image/jpeg");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(compressed));

        assertThat(result.getWidth()).isLessThanOrEqualTo(1920);
        assertThat(result.getHeight()).isLessThanOrEqualTo(1920);
        assertThat(compressed.length).isLessThan(original.size());
    }

    /** 未超尺寸的 PNG 不再重编码：重编码要付解码+编码的 CPU，还不一定更小。 */
    @Test
    void keepsPngWithinLimitsUntouched() throws Exception {
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(image, "png", original);
        byte[] bytes = original.toByteArray();

        byte[] result = new ImageCompressor().compress(bytes, "image/png");

        assertThat(result).isSameAs(bytes);
    }

    /** 未超尺寸的 JPEG 同样原样入库：客户端已按 1920 压过，服务端再算一次纯属浪费。 */
    @Test
    void keepsJpegWithinLimitsUntouched() throws Exception {
        BufferedImage image = new BufferedImage(1920, 1440, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.DARK_GRAY);
            graphics.fillRect(0, 0, 1920, 1440);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", original);
        byte[] bytes = original.toByteArray();

        byte[] result = new ImageCompressor().compress(bytes, "image/jpeg");

        assertThat(result).isSameAs(bytes);
    }

    /** 缩略图：等比缩到最长边以内，统一 JPEG。 */
    @Test
    void thumbnailFitsWithinRequestedSide() throws Exception {
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(image, "png", original);

        byte[] thumb = new ImageCompressor().thumbnail(original.toByteArray(), 480);

        assertThat(thumb).isNotNull();
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(thumb));
        assertThat(Math.max(result.getWidth(), result.getHeight())).isLessThanOrEqualTo(480);
        assertThat(result.getWidth()).isGreaterThan(result.getHeight());
    }
}
