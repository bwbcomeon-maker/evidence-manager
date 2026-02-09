package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.EvidenceListItemVO;
import com.bwbcomeon.evidence.dto.EvidenceResponse;
import com.bwbcomeon.evidence.dto.PermissionBits;
import com.bwbcomeon.evidence.dto.InvalidateAuditInfo;
import com.bwbcomeon.evidence.dto.LatestVersionVO;
import com.bwbcomeon.evidence.dto.PageResult;
import com.bwbcomeon.evidence.enums.EvidenceStatus;
import com.bwbcomeon.evidence.entity.EvidenceItem;
import com.bwbcomeon.evidence.entity.EvidenceVersion;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.mapper.AuthProjectAclMapper;
import com.bwbcomeon.evidence.mapper.EvidenceItemMapper;
import com.bwbcomeon.evidence.mapper.EvidenceVersionMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
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
    private ProjectMapper projectMapper;

    @Autowired
    private AuthProjectAclMapper authProjectAclMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

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
                                          MultipartFile file, Long userId, String roleCode) {
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

        // 2. 权限校验：editor/owner 或 SYSTEM_ADMIN 可上传，viewer 不可
        permissionUtil.checkCanUpload(projectId, userId, roleCode);

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
            evidenceItem.setEvidenceStatus("DRAFT"); // 上传默认为草稿，由用户点击「提交」后变为 SUBMITTED
            evidenceItem.setBizType(bizType); // 设置业务证据类型
            evidenceItem.setCreatedByUserId(userId);
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
            evidenceVersion.setUploaderUserId(userId);
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
            // 兼容：前端统一读 evidenceStatus，旧逻辑若只读 status 则与 evidenceStatus 一致，避免列表误显示「已提交」
            response.setStatus(evidenceItem.getEvidenceStatus() != null ? evidenceItem.getEvidenceStatus() : evidenceItem.getStatus());
            response.setEvidenceStatus(evidenceItem.getEvidenceStatus());
            response.setCreatedByUserId(evidenceItem.getCreatedByUserId());
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
    public List<EvidenceListItemVO> listEvidences(Long projectId, String nameLike, String status, String bizType, String contentType, Long userId, String roleCode) {
        // 1. 权限校验：SYSTEM_ADMIN/PMO 可见全部项目（与 getVisibleProjectIds 一致），其余须为项目创建人或 ACL 成员
        if (roleCode == null || (!"SYSTEM_ADMIN".equals(roleCode) && !"PMO".equals(roleCode))) {
            permissionUtil.checkProjectAccess(projectId, userId);
        }

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
            vo.setEvidenceStatus(item.getEvidenceStatus() != null ? item.getEvidenceStatus() : resolveEvidenceStatusFromOld(item.getStatus()));
            vo.setCreatedByUserId(item.getCreatedByUserId());
            vo.setCreatedAt(item.getCreatedAt());
            vo.setUpdatedAt(item.getUpdatedAt());
            vo.setInvalidReason(item.getInvalidReason());
            vo.setInvalidByUserId(item.getInvalidByUserId());
            vo.setInvalidAt(item.getInvalidAt());

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
            PermissionBits bits = permissionUtil.computeProjectPermissionBits(item.getProjectId(), userId, roleCode);
            vo.setPermissions(bits);
            vo.setCanInvalidate(Boolean.TRUE.equals(bits.getCanInvalidate()));

            result.add(vo);
        }

        logger.info("List evidences: projectId={}, count={}", projectId, result.size());
        return result;
    }

    /**
     * 获取当前用户可见的项目ID列表（权限过滤）
     * SYSTEM_ADMIN/PMO：全部项目；普通用户：其创建的项目 + 其有 ACL 的项目（按 sys_user.id 匹配）
     */
    public List<Long> getVisibleProjectIds(Long currentUserId, String roleCode) {
        if (roleCode != null && ("SYSTEM_ADMIN".equals(roleCode) || "PMO".equals(roleCode))) {
            return projectMapper.selectAll().stream().map(Project::getId).distinct().collect(Collectors.toList());
        }
        if (currentUserId == null) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>();
        for (Project p : projectMapper.selectByCreatedBy(currentUserId)) {
            ids.add(p.getId());
        }
        for (AuthProjectAcl a : authProjectAclMapper.selectBySysUserId(currentUserId)) {
            ids.add(a.getProjectId());
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 分页查询证据（仅可见项目内，支持 projectId/status/uploader/recentDays/fileCategory）
     * 默认不返回作废证据（status=invalid），除非传 status=VOIDED 或 invalid。
     */
    public PageResult<EvidenceListItemVO> pageEvidence(
            int page, int pageSize,
            Long projectId, String status, String uploader, Integer recentDays, String fileCategory, String nameLike,
            Long currentUserId, String roleCode) {
        List<Long> visibleIds = getVisibleProjectIds(currentUserId, roleCode);
        if (visibleIds.isEmpty()) {
            return new PageResult<>(0, new ArrayList<>(), page, pageSize);
        }
        // status: 前端传 VOIDED 时映射为 INVALID；支持 DRAFT/SUBMITTED/ARCHIVED/INVALID
        String statusParam = null;
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            statusParam = "VOIDED".equals(s) ? "INVALID" : s;
        }
        Long createdByUserId = "me".equalsIgnoreCase(uploader != null ? uploader.trim() : "") ? currentUserId : null;
        OffsetDateTime createdAfter = (recentDays != null && recentDays > 0) ? OffsetDateTime.now().minusDays(recentDays) : null;
        String fileCategoryParam = (fileCategory != null && !fileCategory.isBlank()) ? fileCategory.trim().toLowerCase() : null;
        if (fileCategoryParam != null && !Set.of("image", "document", "video").contains(fileCategoryParam)) {
            fileCategoryParam = null;
        }
        long offset = (long) (page - 1) * pageSize;
        int limit = Math.min(Math.max(1, pageSize), 100);

        List<EvidenceItem> items = evidenceItemMapper.selectPageWithFilters(
                visibleIds, projectId, statusParam, createdByUserId, createdAfter, fileCategoryParam, nameLike, offset, limit);
        long total = evidenceItemMapper.countPageWithFilters(
                visibleIds, projectId, statusParam, createdByUserId, createdAfter, fileCategoryParam, nameLike);

        if (items.isEmpty()) {
            return new PageResult<>(total, new ArrayList<>(), page, pageSize);
        }
        List<Long> evidenceIds = items.stream().map(EvidenceItem::getId).collect(Collectors.toList());
        List<EvidenceVersion> latestVersions = evidenceVersionMapper.selectLatestVersionsByEvidenceIds(evidenceIds);
        Map<Long, EvidenceVersion> versionMap = latestVersions.stream().collect(Collectors.toMap(EvidenceVersion::getEvidenceId, v -> v));

        List<EvidenceListItemVO> records = new ArrayList<>();
        for (EvidenceItem item : items) {
            EvidenceListItemVO vo = new EvidenceListItemVO();
            vo.setEvidenceId(item.getId());
            vo.setProjectId(item.getProjectId());
            vo.setTitle(item.getTitle());
            vo.setBizType(item.getBizType());
            vo.setContentType(item.getContentType());
            vo.setStatus(item.getStatus());
            vo.setEvidenceStatus(item.getEvidenceStatus() != null ? item.getEvidenceStatus() : resolveEvidenceStatusFromOld(item.getStatus()));
            vo.setCreatedByUserId(item.getCreatedByUserId());
            vo.setCreatedAt(item.getCreatedAt());
            vo.setUpdatedAt(item.getUpdatedAt());
            vo.setInvalidReason(item.getInvalidReason());
            vo.setInvalidByUserId(item.getInvalidByUserId());
            vo.setInvalidAt(item.getInvalidAt());
            EvidenceVersion latest = versionMap.get(item.getId());
            if (latest != null) {
                LatestVersionVO lv = new LatestVersionVO();
                lv.setVersionId(latest.getId());
                lv.setVersionNo(latest.getVersionNo());
                lv.setOriginalFilename(latest.getOriginalFilename());
                lv.setFilePath(latest.getFilePath());
                lv.setFileSize(latest.getFileSize());
                lv.setCreatedAt(latest.getCreatedAt());
                vo.setLatestVersion(lv);
            }
            PermissionBits bits = permissionUtil.computeProjectPermissionBits(item.getProjectId(), currentUserId, roleCode);
            vo.setPermissions(bits);
            vo.setCanInvalidate(Boolean.TRUE.equals(bits.getCanInvalidate()));
            records.add(vo);
        }
        return new PageResult<>(total, records, page, pageSize);
    }

    /** 兼容旧 status 字段映射到 evidence_status */
    private static String resolveEvidenceStatusFromOld(String status) {
        if (status == null || status.isBlank()) return "SUBMITTED";
        switch (status.toLowerCase()) {
            case "invalid": return "INVALID";
            case "archived": return "ARCHIVED";
            default: return "SUBMITTED";
        }
    }

    /**
     * 根据ID获取证据详情（含最新版本），校验项目访问权限，并返回当前用户是否可作废 canInvalidate
     */
    public EvidenceListItemVO getEvidenceById(Long id, Long userId, String roleCode) {
        EvidenceItem item = evidenceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "证据不存在");
        }
        if (roleCode == null || (!"SYSTEM_ADMIN".equals(roleCode) && !"PMO".equals(roleCode))) {
            permissionUtil.checkProjectAccess(item.getProjectId(), userId);
        }
        EvidenceListItemVO vo = new EvidenceListItemVO();
        vo.setEvidenceId(item.getId());
        vo.setProjectId(item.getProjectId());
        vo.setTitle(item.getTitle());
        vo.setBizType(item.getBizType());
        vo.setNote(item.getNote());
        vo.setContentType(item.getContentType());
        vo.setStatus(item.getStatus());
        vo.setEvidenceStatus(item.getEvidenceStatus() != null ? item.getEvidenceStatus() : resolveEvidenceStatusFromOld(item.getStatus()));
        vo.setCreatedByUserId(item.getCreatedByUserId());
        if (item.getCreatedByUserId() != null) {
            // selectById 不过滤 is_deleted，故即使用户已在「用户管理」中逻辑删除，仍可查到并展示历史上传人姓名
            SysUser creator = sysUserMapper.selectById(item.getCreatedByUserId());
            String displayName = null;
            if (creator != null) {
                if (creator.getRealName() != null && !creator.getRealName().isBlank()) {
                    displayName = creator.getRealName();
                } else if (creator.getUsername() != null && !creator.getUsername().isBlank()) {
                    displayName = creator.getUsername();
                } else {
                    displayName = "用户" + item.getCreatedByUserId();
                }
            }
            vo.setCreatedByDisplayName(displayName);
        }
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        vo.setInvalidReason(item.getInvalidReason());
        vo.setInvalidByUserId(item.getInvalidByUserId());
        vo.setInvalidAt(item.getInvalidAt());
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(item.getProjectId(), userId, roleCode);
        vo.setPermissions(bits);
        vo.setCanInvalidate(Boolean.TRUE.equals(bits.getCanInvalidate()));
        EvidenceVersion latest = evidenceVersionMapper.selectLatestVersionByEvidenceId(item.getId());
        if (latest != null) {
            LatestVersionVO lv = new LatestVersionVO();
            lv.setVersionId(latest.getId());
            lv.setVersionNo(latest.getVersionNo());
            lv.setOriginalFilename(latest.getOriginalFilename());
            lv.setFilePath(latest.getFilePath());
            lv.setFileSize(latest.getFileSize());
            lv.setCreatedAt(latest.getCreatedAt());
            vo.setLatestVersion(lv);
        }
        return vo;
    }

    /**
     * 证据状态流转：提交（DRAFT -> SUBMITTED），需上传权限（owner/editor 或 SYSTEM_ADMIN）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitEvidence(Long id, Long userId, String roleCode) {
        EvidenceItem item = evidenceItemMapper.selectById(id);
        if (item == null) throw new BusinessException(404, "证据不存在");
        permissionUtil.checkCanSubmit(item.getProjectId(), userId, roleCode);
        String current = item.getEvidenceStatus() != null ? item.getEvidenceStatus() : resolveEvidenceStatusFromOld(item.getStatus());
        EvidenceStatus currentStatus = EvidenceStatus.fromCode(current);
        currentStatus.validateTransition(EvidenceStatus.SUBMITTED);
        int n = evidenceItemMapper.updateEvidenceStatus(id, EvidenceStatus.SUBMITTED.getCode(), null, null, currentStatus.getCode());
        if (n == 0) throw new BusinessException(400, "证据状态已变更，请刷新后重试");
    }

    /**
     * 证据状态流转：归档（SUBMITTED -> ARCHIVED），仅项目责任人可操作（与 permissions 同源）
     */
    @Transactional(rollbackFor = Exception.class)
    public void archiveEvidence(Long id, Long userId, String roleCode) {
        EvidenceItem item = evidenceItemMapper.selectById(id);
        if (item == null) throw new BusinessException(404, "证据不存在");
        permissionUtil.checkCanArchive(item.getProjectId(), userId, roleCode);
        String current = item.getEvidenceStatus() != null ? item.getEvidenceStatus() : resolveEvidenceStatusFromOld(item.getStatus());
        EvidenceStatus currentStatus = EvidenceStatus.fromCode(current);
        currentStatus.validateTransition(EvidenceStatus.ARCHIVED);
        OffsetDateTime now = OffsetDateTime.now();
        int n = evidenceItemMapper.updateEvidenceStatus(id, EvidenceStatus.ARCHIVED.getCode(), now, null, currentStatus.getCode());
        if (n == 0) throw new BusinessException(400, "证据状态已变更，请刷新后重试");
    }

    /**
     * 证据状态流转：作废（DRAFT 或 SUBMITTED -> INVALID），仅项目责任人可操作，必填作废原因
     *
     * @return 审计用快照信息（projectId, beforeData, afterData）
     */
    @Transactional(rollbackFor = Exception.class)
    public InvalidateAuditInfo invalidateEvidence(Long id, Long userId, String roleCode, String invalidReason) {
        if (invalidReason == null || invalidReason.isBlank()) {
            throw new BusinessException(400, "作废原因不能为空");
        }
        EvidenceItem item = evidenceItemMapper.selectById(id);
        if (item == null) throw new BusinessException(404, "证据不存在");
        permissionUtil.checkCanInvalidate(item.getProjectId(), userId, roleCode);
        String current = item.getEvidenceStatus() != null ? item.getEvidenceStatus() : resolveEvidenceStatusFromOld(item.getStatus());
        EvidenceStatus.fromCode(current).validateTransition(EvidenceStatus.INVALID);
        OffsetDateTime now = OffsetDateTime.now();
        String beforeData = buildEvidenceSnapshotJson(item.getId(), item.getProjectId(), current, null, null, null);
        int n = evidenceItemMapper.updateEvidenceInvalidate(
                id, EvidenceStatus.INVALID.getCode(), now, invalidReason.trim(), userId, now);
        if (n == 0) throw new BusinessException(400, "已作废或状态不允许");
        String afterData = buildEvidenceSnapshotJson(id, item.getProjectId(), EvidenceStatus.INVALID.getCode(),
                invalidReason.trim(), String.valueOf(userId), now.toString());
        return new InvalidateAuditInfo(item.getProjectId(), beforeData, afterData);
    }

    /** 审计快照至少含 status(evidence_status)、invalid_reason、invalid_by、invalid_at */
    private static String buildEvidenceSnapshotJson(Long evidenceId, Long projectId, String evidenceStatus,
                                                   String invalidReason, String invalidBy, String invalidAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"evidenceId\":").append(evidenceId).append(",\"projectId\":").append(projectId)
                .append(",\"status\":\"").append(escapeJson(evidenceStatus)).append("\"")
                .append(",\"evidenceStatus\":\"").append(escapeJson(evidenceStatus)).append("\"");
        if (invalidReason != null) sb.append(",\"invalid_reason\":\"").append(escapeJson(invalidReason)).append("\"");
        if (invalidBy != null) sb.append(",\"invalid_by\":\"").append(escapeJson(invalidBy)).append("\"");
        if (invalidAt != null) sb.append(",\"invalid_at\":\"").append(escapeJson(invalidAt)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * 下载证据版本文件
     *
     * @param versionId 版本ID
     * @param userId 当前用户ID
     * @param roleCode 当前用户角色（SYSTEM_ADMIN/PMO 可见全部项目）
     * @return 文件资源
     * @throws BusinessException 如果版本不存在、文件不存在或权限不足
     */
    public Resource downloadVersionFile(Long versionId, Long userId, String roleCode) {
        // 1. 查询版本记录
        EvidenceVersion version = evidenceVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "Version not found");
        }

        // 2. 权限校验：SYSTEM_ADMIN/PMO 可访问全部项目，其余须为项目创建人或 ACL 成员
        if (roleCode == null || (!"SYSTEM_ADMIN".equals(roleCode) && !"PMO".equals(roleCode))) {
            permissionUtil.checkProjectAccess(version.getProjectId(), userId);
        }

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
