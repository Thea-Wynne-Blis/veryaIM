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
}
