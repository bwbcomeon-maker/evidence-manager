package com.bwbcomeon.evidence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 图片水印服务（原图保留，生成派生水印图）
 */
@Service
public class ImageWatermarkService {
    private static final Logger logger = LoggerFactory.getLogger(ImageWatermarkService.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Value("${evidence.image.watermark.system-name:项目交付证据管理系统}")
    private String systemName;
    @Value("${evidence.image.watermark.label-project:项目}")
    private String labelProject;
    @Value("${evidence.image.watermark.label-uploader:上传人}")
    private String labelUploader;
    @Value("${evidence.image.watermark.label-upload-time:上传时间}")
    private String labelUploadTime;
    @Value("${evidence.image.watermark.footer:仅供项目内部使用}")
    private String footer;
    @Value("${evidence.image.watermark.fallback-unknown-project:未知}")
    private String fallbackUnknownProject;
    @Value("${evidence.image.watermark.fallback-unknown-uploader:未知}")
    private String fallbackUnknownUploader;

    /**
     * 支持的图片类型：jpg/jpeg/png/webp
     */
    public boolean isSupportedImage(String contentType, String filename) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String ext = fileExtension(filename).toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/jpeg") || ct.startsWith("image/jpg") || ct.startsWith("image/png") || ct.startsWith("image/webp")) {
            return true;
        }
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext);
    }

    /**
     * 生成右下角半透明文字水印图
     */
    public WatermarkResult generateWatermarkedImage(byte[] originalBytes, String contentType, String originalFilename,
                                                    WatermarkContext context) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (src == null) {
            throw new IOException("Unsupported image content");
        }

        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.drawImage(src, 0, 0, null);

            List<String> lines = buildWatermarkLines(context);
            int base = Math.min(width, height);
            int fontSize = Math.max(10, Math.min(24, base / 32));
            int padding = Math.max(8, base / 50);
            Font font = pickFontForLines(lines, fontSize);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int lineHeight = fm.getHeight();
            int blockHeight = lineHeight * lines.size();
            int maxLineWidth = 0;
            for (String line : lines) {
                maxLineWidth = Math.max(maxLineWidth, fm.stringWidth(line));
            }

            int x = Math.max(padding, width - maxLineWidth - padding);
            int y = Math.max(padding + fm.getAscent(), height - blockHeight - padding + fm.getAscent());

            // 深色描边 + 白色填充，保证浅色/深色背景下都清晰可辨；整体透明度 0.35，不过度遮挡内容
            float globalAlpha = 0.35f;
            g2.setComposite(AlphaComposite.SrcOver.derive(globalAlpha));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            FontRenderContext frc = g2.getFontRenderContext();
            for (int i = 0; i < lines.size(); i++) {
                int yy = y + i * lineHeight;
                TextLayout layout = new TextLayout(lines.get(i), font, frc);
                Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(x, yy));
                g2.setColor(new Color(0, 0, 0, 153)); // rgba(0,0,0,0.6)
                g2.draw(outline);
                g2.setColor(new Color(255, 255, 255, 230)); // rgba(255,255,255,0.9)
                g2.fill(outline);
            }
        } finally {
            g2.dispose();
        }

        String sourceFormat = detectPreferredFormat(contentType, originalFilename);
        String outputFormat = chooseWritableFormat(sourceFormat).orElse("png");
        byte[] watermarkedBytes = toBytes(canvas, outputFormat);
        String watermarkedFilename = buildWatermarkedFilename(originalFilename, outputFormat);
        String watermarkedContentType = "image/" + ("jpg".equals(outputFormat) ? "jpeg" : outputFormat);
        return new WatermarkResult(watermarkedBytes, outputFormat, watermarkedFilename, watermarkedContentType);
    }

    public String buildWatermarkedFilename(String originalFilename, String outputFormat) {
        String safeName = (originalFilename == null || originalFilename.isBlank())
                ? "image_" + System.currentTimeMillis() + ".png"
                : originalFilename;
        int dot = safeName.lastIndexOf('.');
        String base = dot > 0 ? safeName.substring(0, dot) : safeName;
        return base + "__wm." + outputFormat;
    }

    /** 统一构造水印文案（使用配置项，空值降级；后续改“项目编号优先”等仅改配置或此处逻辑） */
    private List<String> buildWatermarkLines(WatermarkContext ctx) {
        List<String> lines = new ArrayList<>();
        lines.add(normalizeWatermarkText(systemName != null && !systemName.isBlank() ? systemName : "项目交付证据管理系统"));
        String projLabel = normalizeWatermarkText(labelProject != null && !labelProject.isBlank() ? labelProject : "项目");
        String projVal = (ctx.projectDisplay() == null || ctx.projectDisplay().isBlank()) ? fallbackUnknownProject : ctx.projectDisplay();
        if (projVal == null || projVal.isBlank()) projVal = fallbackUnknownProject != null ? fallbackUnknownProject : "未知";
        lines.add(projLabel + "：" + normalizeWatermarkText(projVal));
        String uploaderLabel = normalizeWatermarkText(labelUploader != null && !labelUploader.isBlank() ? labelUploader : "上传人");
        String uploaderVal = (ctx.uploaderDisplay() == null || ctx.uploaderDisplay().isBlank()) ? fallbackUnknownUploader : ctx.uploaderDisplay();
        if (uploaderVal == null || uploaderVal.isBlank()) uploaderVal = fallbackUnknownUploader != null ? fallbackUnknownUploader : "未知";
        lines.add(uploaderLabel + "：" + normalizeWatermarkText(uploaderVal));
        String timeLabel = normalizeWatermarkText(labelUploadTime != null && !labelUploadTime.isBlank() ? labelUploadTime : "上传时间");
        OffsetDateTime t = ctx.uploadTime() != null ? ctx.uploadTime() : OffsetDateTime.now();
        lines.add(timeLabel + "：" + DT_FMT.format(t));
        lines.add(normalizeWatermarkText(footer != null && !footer.isBlank() ? footer : "仅供项目内部使用"));
        return lines;
    }

    /**
     * 优先选择可完整显示中文的字体，避免 JVM/系统默认字体导致乱码或方块字。
     */
    private static Font pickFontForLines(List<String> lines, int fontSize) {
        String combined = String.join(" ", lines);
        String[] preferredFamilies = new String[]{
                "PingFang SC",
                "Hiragino Sans GB",
                "Microsoft YaHei",
                "SimHei",
                "Noto Sans CJK SC",
                "WenQuanYi Zen Hei",
                "Source Han Sans SC",
                "Arial Unicode MS",
                "Dialog",
                "SansSerif"
        };
        for (String family : preferredFamilies) {
            Font font = new Font(family, Font.PLAIN, fontSize);
            if (font.canDisplayUpTo(combined) == -1) {
                return font;
            }
        }
        return new Font("SansSerif", Font.PLAIN, fontSize);
    }

    /**
     * 纠偏 UTF-8 被误按 ISO-8859-1 解析导致的“é¡¹ç›®”类乱码。
     */
    static String normalizeWatermarkText(String value) {
        if (value == null || value.isBlank()) return value;
        if (containsCjk(value)) return value;
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        if (containsCjk(repaired)) {
            return repaired;
        }
        return value;
    }

    private static boolean containsCjk(String text) {
        if (text == null || text.isBlank()) return false;
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    private static String detectPreferredFormat(String contentType, String filename) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";
        if (ct.contains("png")) return "png";
        if (ct.contains("webp")) return "webp";
        String ext = fileExtension(filename).toLowerCase(Locale.ROOT);
        if ("jpg".equals(ext) || "jpeg".equals(ext)) return "jpg";
        if ("png".equals(ext)) return "png";
        if ("webp".equals(ext)) return "webp";
        return "png";
    }

    private static Optional<String> chooseWritableFormat(String preferred) {
        if (hasWriter(preferred)) return Optional.of(preferred);
        if ("jpg".equals(preferred) && hasWriter("jpeg")) return Optional.of("jpeg");
        if (hasWriter("png")) return Optional.of("png");
        if (hasWriter("jpg")) return Optional.of("jpg");
        if (hasWriter("jpeg")) return Optional.of("jpeg");
        return Optional.empty();
    }

    private static boolean hasWriter(String format) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        return writers != null && writers.hasNext();
    }

    private static String fileExtension(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1) : "";
    }

    private static byte[] toBytes(BufferedImage img, String format) throws IOException {
        // JPG 不支持透明通道，需转 RGB
        BufferedImage output = img;
        if ("jpg".equals(format) || "jpeg".equals(format)) {
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.drawImage(img, 0, 0, null);
            } finally {
                g.dispose();
            }
            output = rgb;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            boolean ok = ImageIO.write(output, format, ios);
            if (!ok) {
                logger.warn("No writer for format {}, fallback to png", format);
                bos.reset();
                ImageIO.write(output, "png", bos);
            }
        }
        return bos.toByteArray();
    }

    public record WatermarkContext(String projectDisplay, String uploaderDisplay, OffsetDateTime uploadTime) {}

    public record WatermarkResult(byte[] bytes, String format, String filename, String contentType) {}
}

