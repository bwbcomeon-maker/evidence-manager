package com.bwbcomeon.evidence.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

class ImageWatermarkServiceTest {

    @Test
    void generateWatermarkedImage_shouldKeepSuffixAndContent() throws Exception {
        ImageWatermarkService service = new ImageWatermarkService();
        byte[] src = createPng(800, 600);

        ImageWatermarkService.WatermarkResult result = service.generateWatermarkedImage(
                src,
                "image/png",
                "sample.png",
                new ImageWatermarkService.WatermarkContext("P-001", "alice", OffsetDateTime.now())
        );

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.bytes());
        Assertions.assertTrue(result.bytes().length > 0);
        Assertions.assertEquals("sample__wm.png", result.filename());
        Assertions.assertEquals("image/png", result.contentType());
    }

    @Test
    void isSupportedImage_shouldAcceptWebpByMimeOrExt() {
        ImageWatermarkService service = new ImageWatermarkService();
        Assertions.assertTrue(service.isSupportedImage("image/webp", "a.bin"));
        Assertions.assertTrue(service.isSupportedImage("application/octet-stream", "a.webp"));
        Assertions.assertFalse(service.isSupportedImage("application/pdf", "a.pdf"));
    }

    @Test
    void isSupportedImage_returnsFalse_forNonImageTypes() {
        ImageWatermarkService service = new ImageWatermarkService();
        Assertions.assertFalse(service.isSupportedImage("application/pdf", "a.pdf"));
        Assertions.assertFalse(service.isSupportedImage("application/msword", "a.doc"));
        Assertions.assertFalse(service.isSupportedImage(null, "x.yaml"));
    }

    @Test
    void isSupportedImage_acceptsJpgJpegPngWebp() {
        ImageWatermarkService service = new ImageWatermarkService();
        Assertions.assertTrue(service.isSupportedImage("image/jpeg", "x.jpg"));
        Assertions.assertTrue(service.isSupportedImage("image/jpeg", "x.jPeG"));
        Assertions.assertTrue(service.isSupportedImage("image/png", "x.png"));
        Assertions.assertTrue(service.isSupportedImage("image/webp", "x.webp"));
    }

    @Test
    void generateWatermarkedImage_jpegContentType_producesWatermarkedFile() throws Exception {
        ImageWatermarkService service = new ImageWatermarkService();
        byte[] src = createPng(400, 300);
        ImageWatermarkService.WatermarkResult result = service.generateWatermarkedImage(
                src, "image/jpeg", "photo.jpg",
                new ImageWatermarkService.WatermarkContext("项目A", "张三", OffsetDateTime.now()));
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.filename().endsWith("__wm.jpg") || result.filename().endsWith("__wm.jpeg"));
        Assertions.assertTrue(result.bytes().length > 0);
    }

    @Test
    void normalizeWatermarkText_shouldRepairUtf8Mojibake() {
        String source = "项目交付证据管理系统";
        String mojibake = new String(source.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        String normalized = ImageWatermarkService.normalizeWatermarkText(mojibake);
        Assertions.assertEquals(source, normalized);
    }

    @Test
    void normalizeWatermarkText_shouldKeepChineseAsIs() {
        String original = "仅供项目内部使用";
        String normalized = ImageWatermarkService.normalizeWatermarkText(original);
        Assertions.assertEquals(original, normalized);
    }

    private static byte[] createPng(int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(new Color(240, 240, 240));
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(50, 120, 210));
            g2.fillRect(30, 30, w - 60, h - 60);
        } finally {
            g2.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }
}

