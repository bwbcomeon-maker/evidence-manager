package com.bwbcomeon.evidence.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件存储工具类
 * MVP阶段：使用本地磁盘存储
 */
@Component
public class FileStorageUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageUtil.class);
    private static final String DEFAULT_FILENAME_PREFIX = "file_";

    @Value("${file.upload.base-path:./data/uploads}")
    private String basePath;

    /**
     * 保存文件到本地磁盘
     * 
     * @param projectId 项目ID
     * @param evidenceId 证据ID
     * @param file 上传的文件
     * @return 文件相对路径（用于保存到数据库的object_key字段）
     * @throws IOException 文件保存失败
     */
    public String saveFile(Long projectId, Long evidenceId, MultipartFile file, String storedFilename) throws IOException {
        Path filePath = resolveSafeStoragePath(projectId, evidenceId, storedFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        logger.info("File saved: {}", filePath.toAbsolutePath());
        return String.format("%d/%d/%s", projectId, evidenceId, filePath.getFileName());
    }

    /**
     * 保存派生文件（如水印图）到与原图相同目录
     *
     * @return 相对路径：{projectId}/{evidenceId}/{filename}
     */
    public String saveDerivedFile(Long projectId, Long evidenceId, String filename, InputStream inputStream) throws IOException {
        Path filePath = resolveSafeStoragePath(projectId, evidenceId, filename);
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Derived file saved: {}", filePath.toAbsolutePath());
        return String.format("%d/%d/%s", projectId, evidenceId, filePath.getFileName());
    }

    public Path resolveRelativePath(String relativePath) {
        return Paths.get(basePath, relativePath).normalize();
    }

    /**
     * 将客户端上传文件名净化为仅可展示/响应头安全使用的文件名，不保留路径信息。
     */
    public String sanitizeClientFilename(String originalFilename) {
        String raw = originalFilename == null ? "" : originalFilename.trim().replace('\\', '/');
        int idx = raw.lastIndexOf('/');
        String basename = idx >= 0 ? raw.substring(idx + 1) : raw;
        basename = basename.replaceAll("[\\r\\n\\t\\x00-\\x1f\\x7f]+", "_");
        basename = basename.replace("\"", "_").replace("'", "_").replace(";", "_");
        basename = basename.replaceAll("\\s+", " ").trim();
        if (basename.isBlank() || ".".equals(basename) || "..".equals(basename)) {
            basename = DEFAULT_FILENAME_PREFIX + System.currentTimeMillis();
        }
        return basename;
    }

    /**
     * 生成实际落盘文件名：使用 UUID 避免碰撞，仅保留安全扩展名。
     */
    public String buildStoredFilename(String sanitizedDisplayFilename) {
        String ext = fileExtension(sanitizedDisplayFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return ext.isEmpty() ? uuid : uuid + "." + ext.toLowerCase(Locale.ROOT);
    }

    /**
     * 计算文件的ETag（MD5）
     */
    public String calculateETag(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Failed to calculate ETag", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * 获取bucket名称（MVP阶段使用固定值"local"）
     */
    public String getBucket() {
        return "local";
    }

    /**
     * 获取已保存文件的完整路径（用于清理）
     * 
     * @param projectId 项目ID
     * @param evidenceId 证据ID
     * @param file 上传的文件
     * @return 文件的完整路径
     */
    public Path getSavedFilePath(String relativePath) {
        return resolveRelativePath(relativePath);
    }

    /**
     * 删除文件（用于事务回滚时的文件清理）
     * 
     * @param filePath 文件路径
     */
    public void deleteFile(Path filePath) {
        try {
            if (filePath != null && Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("Deleted file: {}", filePath.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.warn("Failed to delete file: {}", filePath, e);
        }
    }

    private Path resolveSafeStoragePath(Long projectId, Long evidenceId, String filename) throws IOException {
        Path projectDir = Paths.get(basePath, String.valueOf(projectId), String.valueOf(evidenceId)).normalize();
        Files.createDirectories(projectDir);
        String safeFilename = sanitizeClientFilename(filename);
        Path filePath = projectDir.resolve(safeFilename).normalize();
        if (!filePath.startsWith(projectDir)) {
            throw new IOException("Invalid storage path");
        }
        return filePath;
    }

    private static String fileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1);
    }
}
