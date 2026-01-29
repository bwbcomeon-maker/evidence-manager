package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.EvidenceListItemVO;
import com.bwbcomeon.evidence.dto.EvidenceResponse;
import com.bwbcomeon.evidence.dto.LatestVersionVO;
import com.bwbcomeon.evidence.entity.EvidenceItem;
import com.bwbcomeon.evidence.entity.EvidenceVersion;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.EvidenceItemMapper;
import com.bwbcomeon.evidence.mapper.EvidenceVersionMapper;
import com.bwbcomeon.evidence.util.FileStorageUtil;
import com.bwbcomeon.evidence.util.PermissionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 证据服务
 */
@Service
public class EvidenceService {
    private static final Logger logger = LoggerFactory.getLogger(EvidenceService.class);

    /**
     * 允许的业务证据类型集合
     */
    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of(
            "PLAN", "REPORT", "MINUTES", "TEST", "ACCEPTANCE", "OTHER"
    );

    @Autowired
    private EvidenceItemMapper evidenceItemMapper;

    @Autowired
    private EvidenceVersionMapper evidenceVersionMapper;

    @Autowired
    private PermissionUtil permissionUtil;

    @Value("${file.upload.base-path:./data/uploads}")
    private String basePath;

    @Autowired
    private FileStorageUtil fileStorageUtil;

    /**
     * 上传证据
     * 
     * @param projectId 项目ID
     * @param name 证据名称
     * @param type 业务证据类型（对应bizType字段）
     * @param remark 备注
     * @param file 文件
     * @param userId 当前用户ID
     * @return 证据响应
     */
    @Transactional(rollbackFor = Exception.class)
    public EvidenceResponse uploadEvidence(Long projectId, String name, String type, String remark, 
                                          MultipartFile file, UUID userId) {
        // 1. 参数校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "File is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(400, "Evidence name is required");
        }
        
        // bizType处理：规范化 + 校验允许值
        String bizType;
        if (type != null && !type.trim().isEmpty()) {
            // 统一规范化：trim + toUpperCase
            String normalizedType = type.trim().toUpperCase();
            // 检查是否在允许值集合中，如果不在则使用 OTHER
            bizType = ALLOWED_BIZ_TYPES.contains(normalizedType) ? normalizedType : "OTHER";
        } else {
            // 未传入时使用默认值
            bizType = "OTHER";
        }

        // 2. 权限校验：检查用户是否有上传权限
        permissionUtil.checkProjectUploadPermission(projectId, userId);

        // 3. 保存文件到本地磁盘
        String objectKey = null;
        String etag;
        Long evidenceId = null;
        Path savedFilePath = null;
        
        try {
            // 先创建evidence记录以获取ID
            EvidenceItem evidenceItem = new EvidenceItem();
            evidenceItem.setProjectId(projectId);
            evidenceItem.setTitle(name);
            evidenceItem.setNote(remark);
            evidenceItem.setContentType(file.getContentType());
            evidenceItem.setSizeBytes(file.getSize());
            evidenceItem.setStatus("active");
            evidenceItem.setBizType(bizType); // 设置业务证据类型
            evidenceItem.setCreatedBy(userId);
            evidenceItem.setCreatedAt(OffsetDateTime.now());
            evidenceItem.setUpdatedAt(OffsetDateTime.now());
            evidenceItem.setBucket(fileStorageUtil.getBucket());
            // 临时设置objectKey，稍后会更新
            evidenceItem.setObjectKey("temp");

            // 插入evidence记录
            int insertResult = evidenceItemMapper.insert(evidenceItem);
            if (insertResult <= 0) {
                throw new BusinessException(500, "Failed to create evidence record");
            }

            evidenceId = evidenceItem.getId();
            logger.info("Created evidence record with id: {}", evidenceId);

            // 保存文件
            objectKey = fileStorageUtil.saveFile(projectId, evidenceId, file);
            savedFilePath = fileStorageUtil.getSavedFilePath(projectId, evidenceId, file);
            etag = fileStorageUtil.calculateETag(file);

            // 更新evidence记录的objectKey和etag
            evidenceItem.setObjectKey(objectKey);
            evidenceItem.setEtag(etag);
            evidenceItemMapper.update(evidenceItem);

            logger.info("Created evidence record: projectId={}, evidenceId={}, objectKey={}, bizType={}", 
                       projectId, evidenceId, objectKey, bizType);

            // 4. 创建版本记录（首次上传固定version_no=1）
            // 获取原始文件名
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                originalFilename = "file_" + System.currentTimeMillis();
            }

            // 创建版本记录
            EvidenceVersion evidenceVersion = new EvidenceVersion();
            evidenceVersion.setEvidenceId(evidenceId);
            evidenceVersion.setProjectId(projectId);
            evidenceVersion.setVersionNo(1); // 首次上传固定为1
            evidenceVersion.setOriginalFilename(originalFilename);
            evidenceVersion.setFilePath(objectKey); // 相对路径，与本地存储真实路径对应
            evidenceVersion.setFileSize(file.getSize());
            evidenceVersion.setContentType(file.getContentType());
            evidenceVersion.setUploaderId(userId);
            evidenceVersion.setRemark(remark);
            evidenceVersion.setCreatedAt(OffsetDateTime.now());

            int versionInsertResult = evidenceVersionMapper.insert(evidenceVersion);
            if (versionInsertResult <= 0) {
                // 如果版本记录插入失败，需要清理已保存的文件
                fileStorageUtil.deleteFile(savedFilePath);
                throw new BusinessException(500, "Failed to create evidence version record");
            }

            logger.info("Created evidence version: evidenceId={}, versionNo=1, filePath={}", 
                       evidenceId, objectKey);

            // 5. 构建响应
            EvidenceResponse response = new EvidenceResponse();
            response.setId(evidenceItem.getId());
            response.setProjectId(evidenceItem.getProjectId());
            response.setTitle(evidenceItem.getTitle());
            response.setNote(evidenceItem.getNote());
            response.setContentType(evidenceItem.getContentType());
            response.setSizeBytes(evidenceItem.getSizeBytes());
            response.setStatus(evidenceItem.getStatus());
            response.setCreatedBy(evidenceItem.getCreatedBy());
            response.setCreatedAt(evidenceItem.getCreatedAt());

            return response;

        } catch (IOException e) {
            logger.error("Failed to save file", e);
            // 如果文件已保存但后续操作失败，清理文件
            if (savedFilePath != null) {
                fileStorageUtil.deleteFile(savedFilePath);
            }
            throw new BusinessException(500, "Failed to save file: " + e.getMessage());
        } catch (BusinessException e) {
            // 业务异常：如果文件已保存但DB操作失败，清理文件
            if (savedFilePath != null) {
                fileStorageUtil.deleteFile(savedFilePath);
            }
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during evidence upload", e);
            // 其他异常：如果文件已保存，清理文件
            if (savedFilePath != null) {
                fileStorageUtil.deleteFile(savedFilePath);
            }
            throw new BusinessException(500, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 按项目查询证据列表
     * 
     * @param projectId 项目ID
     * @param nameLike 证据名称模糊匹配（可选）
     * @param status 证据状态（可选）
     * @param bizType 业务证据类型（可选，如PLAN/REPORT/MINUTES/TEST/ACCEPTANCE/OTHER）
     * @param contentType 文件类型（MIME类型，可选，如application/pdf）
     * @param userId 当前用户ID
     * @return 证据列表
     */
    public List<EvidenceListItemVO> listEvidences(Long projectId, String nameLike, String status, String bizType, String contentType, UUID userId) {
        // 1. 权限校验
        // TODO: 当前退化为检查project.created_by = 当前用户，后续需要实现成员表检查
        // TODO: ADMIN 可查看全部，当前未实现 ADMIN 角色检查
        permissionUtil.checkProjectAccess(projectId, userId);

        // 2. bizType规范化处理（如果传入，转换为大写以匹配数据库存储格式）
        String normalizedBizType = null;
        if (bizType != null && !bizType.trim().isEmpty()) {
            normalizedBizType = bizType.trim().toUpperCase();
        }

        // 3. 查询证据列表（带过滤条件）
        List<EvidenceItem> evidenceItems = evidenceItemMapper.selectByProjectIdWithFilters(
                projectId, nameLike, status, normalizedBizType, contentType);

        if (evidenceItems.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 批量查询最新版本
        List<Long> evidenceIds = evidenceItems.stream()
                .map(EvidenceItem::getId)
                .collect(Collectors.toList());
        
        List<EvidenceVersion> latestVersions = evidenceVersionMapper.selectLatestVersionsByEvidenceIds(evidenceIds);
        
        // 构建evidenceId -> LatestVersion的映射
        Map<Long, EvidenceVersion> versionMap = latestVersions.stream()
                .collect(Collectors.toMap(EvidenceVersion::getEvidenceId, v -> v));

        // 4. 组装返回数据
        List<EvidenceListItemVO> result = new ArrayList<>();
        for (EvidenceItem item : evidenceItems) {
            EvidenceListItemVO vo = new EvidenceListItemVO();
            vo.setEvidenceId(item.getId());
            vo.setProjectId(item.getProjectId());
            vo.setTitle(item.getTitle());
            vo.setBizType(item.getBizType());
            vo.setContentType(item.getContentType());
            vo.setStatus(item.getStatus());
            vo.setCreatedBy(item.getCreatedBy());
            vo.setCreatedAt(item.getCreatedAt());
            vo.setUpdatedAt(item.getUpdatedAt());

            // 设置最新版本信息
            EvidenceVersion latestVersion = versionMap.get(item.getId());
            if (latestVersion != null) {
                LatestVersionVO latestVersionVO = new LatestVersionVO();
                latestVersionVO.setVersionId(latestVersion.getId());
                latestVersionVO.setVersionNo(latestVersion.getVersionNo());
                latestVersionVO.setOriginalFilename(latestVersion.getOriginalFilename());
                latestVersionVO.setFilePath(latestVersion.getFilePath());
                latestVersionVO.setFileSize(latestVersion.getFileSize());
                latestVersionVO.setCreatedAt(latestVersion.getCreatedAt());
                vo.setLatestVersion(latestVersionVO);
            }

            result.add(vo);
        }

        logger.info("List evidences: projectId={}, count={}", projectId, result.size());
        return result;
    }

    /**
     * 下载证据版本文件
     * 
     * @param versionId 版本ID
     * @param userId 当前用户ID
     * @return 文件资源
     * @throws BusinessException 如果版本不存在、文件不存在或权限不足
     */
    public Resource downloadVersionFile(Long versionId, UUID userId) {
        // 1. 查询版本记录
        EvidenceVersion version = evidenceVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "Version not found");
        }

        // 2. 权限校验：检查用户是否有项目访问权限
        // TODO: ADMIN 可下载全部，当前未实现 ADMIN 角色检查
        permissionUtil.checkProjectAccess(version.getProjectId(), userId);

        // 3. 构建文件完整路径
        String filePath = version.getFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new BusinessException(500, "File path is empty in version record");
        }

        // 文件路径格式：{projectId}/{evidenceId}/{originalFilename}
        // 完整路径：./data/uploads/{filePath}
        Path fullPath = Paths.get(basePath, filePath).normalize();
        
        // 安全检查：确保路径在 basePath 目录下，防止路径遍历攻击
        Path basePathNormalized = Paths.get(basePath).normalize();
        if (!fullPath.startsWith(basePathNormalized)) {
            throw new BusinessException(400, "Invalid file path");
        }

        // 4. 检查文件是否存在
        if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
            throw new BusinessException(404, "File not found on disk: " + filePath);
        }

        try {
            // 5. 创建资源对象
            Resource resource = new UrlResource(fullPath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException(500, "File is not readable");
            }

            logger.info("Download version file: versionId={}, filePath={}, originalFilename={}", 
                       versionId, filePath, version.getOriginalFilename());
            
            return resource;
        } catch (IOException e) {
            logger.error("Failed to create resource for file: {}", fullPath, e);
            throw new BusinessException(500, "Failed to read file: " + e.getMessage());
        }
    }

    /**
     * 获取版本文件的原始文件名
     * 
     * @param versionId 版本ID
     * @return 原始文件名
     */
    public String getVersionOriginalFilename(Long versionId) {
        EvidenceVersion version = evidenceVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "Version not found");
        }
        return version.getOriginalFilename();
    }

    /**
     * 获取版本文件的Content-Type
     * 
     * @param versionId 版本ID
     * @return Content-Type
     */
    public String getVersionContentType(Long versionId) {
        EvidenceVersion version = evidenceVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "Version not found");
        }
        return version.getContentType();
    }
}
