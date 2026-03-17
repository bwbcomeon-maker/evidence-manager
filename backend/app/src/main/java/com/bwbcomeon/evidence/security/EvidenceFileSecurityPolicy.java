package com.bwbcomeon.evidence.security;

import com.bwbcomeon.evidence.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 证据文件安全策略：上传白名单、简单文件签名校验、inline 预览白名单。
 */
@Component
public class EvidenceFileSecurityPolicy {

    private static final Map<String, String> CANONICAL_MIME_BY_EXT = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("zip", "application/zip")
    );

    private static final Set<String> INLINE_SAFE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    public ValidatedUploadFile validateUpload(MultipartFile file, String sanitizedFilename) {
        String ext = fileExtension(sanitizedFilename);
        if (!CANONICAL_MIME_BY_EXT.containsKey(ext)) {
            throw new BusinessException(400, "仅支持 jpg、jpeg、png、webp、pdf、doc、docx、xls、xlsx、ppt、pptx、zip 文件");
        }
        try {
            if (!matchesSignature(file, ext)) {
                throw new BusinessException(400, "文件内容与扩展名不匹配，请重新选择正确的文件");
            }
        } catch (IOException e) {
            throw new BusinessException(400, "无法识别上传文件，请重新选择");
        }
        return new ValidatedUploadFile(sanitizedFilename, CANONICAL_MIME_BY_EXT.get(ext));
    }

    public boolean isInlinePreviewAllowed(String contentType, String filename) {
        String normalizedContentType = normalizeContentType(contentType);
        if (INLINE_SAFE_MIME_TYPES.contains(normalizedContentType)) {
            return true;
        }
        String ext = fileExtension(filename);
        return INLINE_SAFE_MIME_TYPES.contains(CANONICAL_MIME_BY_EXT.get(ext));
    }

    private boolean matchesSignature(MultipartFile file, String ext) throws IOException {
        return switch (ext) {
            case "jpg", "jpeg" -> hasPrefix(file, new int[]{0xFF, 0xD8, 0xFF});
            case "png" -> hasPrefix(file, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "webp" -> isWebp(file);
            case "pdf" -> hasPrefix(file, new int[]{0x25, 0x50, 0x44, 0x46, 0x2D});
            case "doc", "xls", "ppt" -> hasPrefix(file, new int[]{0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1});
            case "docx" -> isOpenXmlDocument(file, "word/");
            case "xlsx" -> isOpenXmlDocument(file, "xl/");
            case "pptx" -> isOpenXmlDocument(file, "ppt/");
            case "zip" -> isZip(file);
            default -> false;
        };
    }

    private boolean hasPrefix(MultipartFile file, int[] expected) throws IOException {
        byte[] actual = readLeadingBytes(file, expected.length + 8);
        if (actual.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((actual[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(MultipartFile file) throws IOException {
        byte[] actual = readLeadingBytes(file, 12);
        return actual.length >= 12
                && actual[0] == 'R' && actual[1] == 'I' && actual[2] == 'F' && actual[3] == 'F'
                && actual[8] == 'W' && actual[9] == 'E' && actual[10] == 'B' && actual[11] == 'P';
    }

    private boolean isZip(MultipartFile file) throws IOException {
        byte[] actual = readLeadingBytes(file, 4);
        return actual.length >= 4 && actual[0] == 'P' && actual[1] == 'K';
    }

    private boolean isOpenXmlDocument(MultipartFile file, String requiredPrefix) throws IOException {
        if (!isZip(file)) {
            return false;
        }
        boolean hasContentTypes = false;
        boolean hasRequiredPrefix = false;
        try (InputStream in = file.getInputStream(); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) {
                    hasContentTypes = true;
                }
                if (name != null && name.startsWith(requiredPrefix)) {
                    hasRequiredPrefix = true;
                }
                if (hasContentTypes && hasRequiredPrefix) {
                    return true;
                }
            }
        }
        return false;
    }

    private byte[] readLeadingBytes(MultipartFile file, int maxBytes) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(maxBytes);
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private static String fileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record ValidatedUploadFile(String filename, String contentType) {
    }
}
