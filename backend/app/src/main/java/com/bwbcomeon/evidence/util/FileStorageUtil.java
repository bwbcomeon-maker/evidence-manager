package com.bwbcomeon.evidence.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 文件存储工具类
 * MVP阶段：使用本地磁盘存储
 */
@Component
public class FileStorageUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageUtil.class);

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
    public String saveFile(Long projectId, Long evidenceId, MultipartFile file) throws IOException {
        // 构建文件存储路径：./data/uploads/{projectId}/{evidenceId}/{originalFilename}
        Path projectDir = Paths.get(basePath, String.valueOf(projectId), String.valueOf(evidenceId));
        
        // 确保目录存在
        Files.createDirectories(projectDir);
        
        // 获取原始文件名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "file_" + System.currentTimeMillis();
        }
        
        // 构建完整文件路径
        Path filePath = projectDir.resolve(originalFilename);
        
        // 保存文件
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("File saved: {}", filePath.toAbsolutePath());
        
        // 返回相对路径（相对于basePath）
        // 格式：{projectId}/{evidenceId}/{originalFilename}
        return String.format("%d/%d/%s", projectId, evidenceId, originalFilename);
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
    public Path getSavedFilePath(Long projectId, Long evidenceId, MultipartFile file) {
        Path projectDir = Paths.get(basePath, String.valueOf(projectId), String.valueOf(evidenceId));
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "file_" + System.currentTimeMillis();
        }
        return projectDir.resolve(originalFilename);
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
}
