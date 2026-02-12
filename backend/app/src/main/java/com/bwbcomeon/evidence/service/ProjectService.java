package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.AddProjectMemberRequest;
import com.bwbcomeon.evidence.dto.BatchAddProjectMembersRequest;
import com.bwbcomeon.evidence.dto.BatchAssignResult;
import com.bwbcomeon.evidence.dto.BatchAssignUserToProjectsRequest;
import com.bwbcomeon.evidence.dto.PermissionBits;
import com.bwbcomeon.evidence.dto.ArchiveBlockVO;
import com.bwbcomeon.evidence.dto.ArchiveResult;
import com.bwbcomeon.evidence.dto.ProjectImportResult;
import com.bwbcomeon.evidence.dto.ProjectVO;
import com.bwbcomeon.evidence.dto.ProjectMemberVO;
import com.bwbcomeon.evidence.dto.StageProgressVO;
import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.AuthProjectAclMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import com.bwbcomeon.evidence.util.PermissionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目服务（P0-3 创建项目；P0-2 列表/详情只读）
 */
@Service
public class ProjectService {

    private static final String ROLE_OWNER = "owner";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ARCHIVED = "archived";
    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AuthProjectAclMapper authProjectAclMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private StageProgressService stageProgressService;

    @Autowired
    private PermissionUtil permissionUtil;

    private static final int KEY_MISSING_SUMMARY_MAX = 5;

    private static final Set<String> ACL_ROLES = Set.of("owner", "editor", "viewer");
    private static final int IMPORT_MAX_ROWS = 500;

    /**
     * PMO Excel 批量导入项目（最小版）：模板列 项目令号、项目名称、项目描述；按 code upsert；仅 SYSTEM_ADMIN/PMO 可调。
     */
    public ProjectImportResult importProjectsFromExcel(InputStream inputStream, Long operatorUserId, String roleCode) {
        if (roleCode == null || (!"SYSTEM_ADMIN".equals(roleCode) && !"PMO".equals(roleCode))) {
            throw new BusinessException(403, "仅 SYSTEM_ADMIN 或 PMO 可导入项目");
        }
        ProjectImportResult result = new ProjectImportResult();
        List<ProjectImportResult.RowResult> details = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = Math.min(sheet.getLastRowNum(), IMPORT_MAX_ROWS);
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                int rowNum = r + 1;
                String code = getCellString(row, 0);
                String name = getCellString(row, 1);
                String description = getCellString(row, 2);
                if (code == null || code.isBlank()) {
                    details.add(new ProjectImportResult.RowResult(rowNum, "", false, "项目令号为空"));
                    continue;
                }
                code = code.trim();
                String nameVal = name != null ? name.trim() : "";
                String descVal = description != null ? description.trim() : "";
                try {
                    Project existing = projectMapper.selectByCode(code);
                    if (existing != null) {
                        existing.setName(nameVal);
                        existing.setDescription(descVal);
                        existing.setUpdatedAt(OffsetDateTime.now());
                        projectMapper.update(existing);
                        details.add(new ProjectImportResult.RowResult(rowNum, code, true, "已更新"));
                    } else {
                        Project project = new Project();
                        project.setCode(code);
                        project.setName(nameVal);
                        project.setDescription(descVal);
                        project.setStatus(STATUS_ACTIVE);
                        project.setCreatedByUserId(operatorUserId);
                        projectMapper.insert(project);
                        // 批量导入的项目不自动添加操作人为成员，成员需后续在成员管理中分配
                        details.add(new ProjectImportResult.RowResult(rowNum, code, true, "已新建"));
                    }
                } catch (Exception e) {
                    details.add(new ProjectImportResult.RowResult(rowNum, code, false, e.getMessage() != null ? e.getMessage() : "导入失败"));
                }
            }
            result.setTotal(details.size());
            result.setSuccessCount((int) details.stream().filter(ProjectImportResult.RowResult::isSuccess).count());
            result.setFailCount(result.getTotal() - result.getSuccessCount());
            result.setDetails(details);
        } catch (Exception e) {
            throw new BusinessException(400, "解析 Excel 失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
        return result;
    }

    private static String getCellString(Row row, int col) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        CellType ct = cell.getCellType();
        if (ct == CellType.STRING) return cell.getStringCellValue();
        if (ct == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    /**
     * 创建项目，并写入一条 owner ACL（项目令号由调用方传入，不自动生成）
     *
     * @param userId      当前用户 sys_user.id
     * @param code        项目令号（必填，唯一）
     * @param name        项目名称（必填）
     * @param description 项目描述（可选）
     * @return 新建项目的基本信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO createProject(Long userId, String code, String name, String description) {
        String codeTrim = code != null ? code.trim() : "";
        if (codeTrim.isEmpty()) {
            throw new BusinessException(400, "项目令号不能为空");
        }
        if (projectMapper.selectByCode(codeTrim) != null) {
            throw new BusinessException(400, "项目令号已存在");
        }
        Project project = new Project();
        project.setCode(codeTrim);
        project.setName(name != null ? name.trim() : "");
        project.setDescription(description != null ? description.trim() : null);
        project.setStatus(STATUS_ACTIVE);
        project.setCreatedByUserId(userId);
        projectMapper.insert(project);

        AuthProjectAcl acl = new AuthProjectAcl();
        acl.setProjectId(project.getId());
        acl.setSysUserId(userId);
        acl.setRole(ROLE_OWNER);
        authProjectAclMapper.insert(acl);

        return toVO(project);
    }

    /**
     * 当前用户可见的项目列表（SYSTEM_ADMIN 全部；普通用户：自己创建的 + ACL 可见）
     * 使用批量查询避免 N+1，并用只读事务统一连接，减少超时与 SqlSession 未同步告警。
     */
    @Transactional(readOnly = true)
    public List<ProjectVO> listVisibleProjects(Long currentUserId, String roleCode) {
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(currentUserId, roleCode);
        if (visibleIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Project> projects = projectMapper.selectByIds(visibleIds);
        List<AuthProjectAcl> allAcls = authProjectAclMapper.selectByProjectIds(visibleIds);
        Map<Long, Long> projectIdToOwnerUserId = new HashMap<>();
        for (AuthProjectAcl acl : allAcls) {
            if (ROLE_OWNER.equals(acl.getRole())) {
                projectIdToOwnerUserId.putIfAbsent(acl.getProjectId(), acl.getSysUserId());
            }
        }
        Set<Long> ownerUserIds = new HashSet<>(projectIdToOwnerUserId.values());
        List<SysUser> ownerUsers = ownerUserIds.isEmpty() ? new ArrayList<>() : sysUserMapper.selectByIds(new ArrayList<>(ownerUserIds));
        Map<Long, String> userIdToDisplayName = new HashMap<>();
        for (SysUser u : ownerUsers) {
            userIdToDisplayName.put(u.getId(), resolveUserDisplayName(u));
        }
        List<ProjectVO> result = new ArrayList<>(projects.size());
        for (Project p : projects) {
            ProjectVO vo = toVO(p);
            Long pmUserId = projectIdToOwnerUserId.get(p.getId());
            if (pmUserId != null) {
                vo.setCurrentPmUserId(pmUserId);
                vo.setCurrentPmDisplayName(userIdToDisplayName.get(pmUserId));
            }
            result.add(vo);
        }
        Map<Long, StageProgressVO> progressMap = stageProgressService.computeStageProgressBatch(visibleIds);
        for (ProjectVO vo : result) {
            if (vo.getId() == null) continue;
            StageProgressVO progress = progressMap.get(vo.getId());
            if (progress != null) {
                vo.setEvidenceCompletionPercent(progress.getOverallCompletionPercent());
                List<String> keyMissing = progress.getKeyMissing();
                if (keyMissing != null && !keyMissing.isEmpty()) {
                    vo.setKeyMissingSummary(keyMissing.subList(0, Math.min(KEY_MISSING_SUMMARY_MAX, keyMissing.size())));
                }
            }
        }
        return result;
    }

    /** 用户展示名：real_name 非空用 real_name，否则用 username */
    private static String resolveUserDisplayName(SysUser user) {
        if (user == null) return null;
        String real = user.getRealName();
        if (real != null && !real.isBlank()) return real.trim();
        return user.getUsername() != null ? user.getUsername() : null;
    }

    /**
     * 项目详情（做可见性校验，不可见返回 403），并返回当前用户是否可作废证据 canInvalidate
     */
    public ProjectVO getProjectDetail(Long projectId, Long currentUserId, String roleCode) {
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(currentUserId, roleCode);
        if (!visibleIds.contains(projectId)) {
            throw new BusinessException(403, "无权限访问该项目");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        ProjectVO vo = toVO(project);
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(projectId, currentUserId, roleCode);
        vo.setPermissions(bits);
        vo.setCanInvalidate(Boolean.TRUE.equals(bits.getCanInvalidate()));
        vo.setCanManageMembers(Boolean.TRUE.equals(bits.getCanManageMembers()));
        vo.setCanUpload(Boolean.TRUE.equals(bits.getCanUpload()));
        Long pmUserId = resolveAclOwnerUserId(projectId);
        if (pmUserId != null) {
            vo.setCurrentPmUserId(pmUserId);
            SysUser pmUser = sysUserMapper.selectById(pmUserId);
            vo.setCurrentPmDisplayName(pmUser != null ? resolveUserDisplayName(pmUser) : null);
        }
        return vo;
    }

    /** 仅 ACL 中 role=owner 的用户（用于展示「项目经理」；无则视为未分配） */
    private Long resolveAclOwnerUserId(Long projectId) {
        List<AuthProjectAcl> acls = authProjectAclMapper.selectByProjectId(projectId);
        for (AuthProjectAcl acl : acls) {
            if (ROLE_OWNER.equals(acl.getRole())) {
                return acl.getSysUserId();
            }
        }
        return null;
    }

    /** 当前项目经理：ACL 中 role=owner 的用户（V1 唯一）；若无则取 project.created_by_user_id（用于权限等逻辑） */
    private Long resolveCurrentPmUserId(Long projectId, Project project) {
        List<AuthProjectAcl> acls = authProjectAclMapper.selectByProjectId(projectId);
        for (AuthProjectAcl acl : acls) {
            if (ROLE_OWNER.equals(acl.getRole())) {
                return acl.getSysUserId();
            }
        }
        return project.getCreatedByUserId();
    }

    /**
     * 项目成员列表（可见项目即可查看；成员来自 auth_project_acl + sys_user）
     */
    public List<ProjectMemberVO> listMembers(Long projectId, Long currentUserId, String roleCode) {
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(currentUserId, roleCode);
        if (!visibleIds.contains(projectId)) {
            throw new BusinessException(403, "无权限访问该项目");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        List<AuthProjectAcl> acls = authProjectAclMapper.selectByProjectId(projectId);
        if (acls.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> userIds = acls.stream().map(AuthProjectAcl::getSysUserId).distinct().collect(Collectors.toList());
        List<SysUser> users = sysUserMapper.selectByIds(userIds);
        java.util.Map<Long, SysUser> userMap = users.stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        List<ProjectMemberVO> result = new ArrayList<>(acls.size());
        for (AuthProjectAcl acl : acls) {
            SysUser u = userMap.get(acl.getSysUserId());
            ProjectMemberVO vo = new ProjectMemberVO(
                    acl.getSysUserId(),
                    acl.getRole(),
                    u != null ? u.getUsername() : null,
                    u != null ? u.getRealName() : null,
                    null
            );
            vo.setIsCurrentUser(currentUserId != null && currentUserId.equals(acl.getSysUserId()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 项目归档：门禁通过则更新 status=archived，否则返回结构化失败信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ArchiveResult archive(Long projectId, Long currentUserId, String roleCode) {
        permissionUtil.checkCanArchive(projectId, currentUserId, roleCode);
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(currentUserId, roleCode);
        if (!visibleIds.contains(projectId)) {
            throw new BusinessException(403, "无权限访问该项目");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (STATUS_ARCHIVED.equals(project.getStatus())) {
            return ArchiveResult.ok();
        }
        StageProgressVO progress = stageProgressService.computeStageProgress(projectId);
        if (progress == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (!progress.isCanArchive()) {
            ArchiveBlockVO block = new ArchiveBlockVO();
            block.setArchiveBlockReason(progress.getArchiveBlockReason());
            block.setKeyMissing(progress.getKeyMissing());
            block.setBlockedByStages(progress.getBlockedByStages());
            block.setBlockedByRequiredItems(progress.getBlockedByRequiredItems());
            return ArchiveResult.fail(block);
        }
        project.setStatus(STATUS_ARCHIVED);
        project.setUpdatedAt(OffsetDateTime.now());
        projectMapper.update(project);
        return ArchiveResult.ok();
    }

    /**
     * 批量将一人分配至多个项目（仅 PMO / SYSTEM_ADMIN）。
     * 按项目逐个执行，单项目失败不影响其他，返回成功数、失败数及失败原因。
     */
    public BatchAssignResult batchAssignUserToProjects(BatchAssignUserToProjectsRequest request,
                                                       Long operatorUserId, String roleCode) {
        if (request == null || request.getUserId() == null || request.getProjectIds() == null || request.getProjectIds().isEmpty()) {
            throw new BusinessException(400, "userId 与 projectIds 不能为空");
        }
        if (roleCode == null || (!"SYSTEM_ADMIN".equals(roleCode) && !"PMO".equals(roleCode))) {
            throw new BusinessException(403, "仅 PMO 或系统管理员可批量分配用户到多项目");
        }
        String role = request.getRole() != null ? request.getRole().trim().toLowerCase() : "editor";
        if (!ACL_ROLES.contains(role)) {
            throw new BusinessException(400, "role 必须为 owner / editor / viewer");
        }
        if (request.getUserId().equals(operatorUserId)) {
            throw new BusinessException(403, "不能为自己批量分配项目");
        }
        if (sysUserMapper.selectById(request.getUserId()) == null) {
            throw new BusinessException(400, "用户不存在");
        }
        AddProjectMemberRequest body = new AddProjectMemberRequest();
        body.setUserId(request.getUserId());
        body.setRole(role);
        int successCount = 0;
        List<String> errors = new ArrayList<>();
        for (Long projectId : request.getProjectIds()) {
            try {
                addOrUpdateMember(projectId, operatorUserId, roleCode, body);
                successCount++;
            } catch (BusinessException e) {
                errors.add("项目" + projectId + ": " + (e.getMessage() != null ? e.getMessage() : "失败"));
            }
        }
        return BatchAssignResult.of(successCount, errors.size(), errors);
    }

    /**
     * 批量为一个项目添加/调整多名成员（含项目经理 owner）。
     * 仅对当前项目有管理成员权限的用户可调用；逐条执行，单条失败不影响其他。
     */
    public BatchAssignResult batchAddProjectMembers(Long projectId, BatchAddProjectMembersRequest request,
                                                    Long operatorUserId, String roleCode) {
        if (request == null || request.getMembers() == null || request.getMembers().isEmpty()) {
            throw new BusinessException(400, "members 不能为空");
        }
        permissionUtil.checkCanManageMembers(projectId, operatorUserId, roleCode);
        int successCount = 0;
        List<String> errors = new ArrayList<>();
        for (AddProjectMemberRequest one : request.getMembers()) {
            if (one == null || one.getUserId() == null) {
                errors.add("成员项缺少 userId");
                continue;
            }
            try {
                addOrUpdateMember(projectId, operatorUserId, roleCode, one);
                successCount++;
            } catch (BusinessException e) {
                errors.add("用户" + one.getUserId() + ": " + (e.getMessage() != null ? e.getMessage() : "失败"));
            }
        }
        return BatchAssignResult.of(successCount, errors.size(), errors);
    }

    /**
     * 添加或调整项目成员（SYSTEM_ADMIN/PMO/项目负责人）。
     * V1：每项目最多一个 owner；分配 PM = 事务内删旧 owner + 增新 owner。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdateMember(Long projectId, Long operatorUserId, String roleCode, AddProjectMemberRequest body) {
        if (body == null || body.getUserId() == null) {
            throw new BusinessException(400, "userId 不能为空");
        }
        String role = body.getRole() != null ? body.getRole().trim().toLowerCase() : null;
        if (role == null || !ACL_ROLES.contains(role)) {
            throw new BusinessException(400, "role 必须为 owner / editor / viewer");
        }
        permissionUtil.checkCanManageMembers(projectId, operatorUserId, roleCode);
        if (body.getUserId().equals(operatorUserId)) {
            throw new BusinessException(403, "不能修改自己");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (sysUserMapper.selectById(body.getUserId()) == null) {
            throw new BusinessException(400, "用户不存在");
        }
        Long targetUserId = body.getUserId();
        AuthProjectAcl existing = authProjectAclMapper.selectByProjectIdAndSysUserId(projectId, targetUserId);

        if (ROLE_OWNER.equals(role)) {
            // 每项目唯一 owner：先移除其他所有 owner，再设目标用户为 owner（同一事务）
            List<AuthProjectAcl> acls = authProjectAclMapper.selectByProjectId(projectId);
            for (AuthProjectAcl acl : acls) {
                if (ROLE_OWNER.equals(acl.getRole()) && !acl.getSysUserId().equals(targetUserId)) {
                    authProjectAclMapper.deleteByProjectIdAndSysUserId(projectId, acl.getSysUserId());
                }
            }
        }

        if (existing != null) {
            existing.setRole(role);
            authProjectAclMapper.update(existing);
        } else {
            AuthProjectAcl acl = new AuthProjectAcl();
            acl.setProjectId(projectId);
            acl.setSysUserId(targetUserId);
            acl.setRole(role);
            authProjectAclMapper.insert(acl);
        }
    }

    /**
     * 移除项目成员（仅 owner 或 SYSTEM_ADMIN）；不允许移除最后一个 owner
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long projectId, Long memberUserId, Long operatorUserId, String roleCode) {
        if (memberUserId == null) {
            throw new BusinessException(400, "userId 不能为空");
        }
        permissionUtil.checkCanManageMembers(projectId, operatorUserId, roleCode);
        if (memberUserId.equals(operatorUserId)) {
            throw new BusinessException(403, "不能修改自己");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        List<AuthProjectAcl> acls = authProjectAclMapper.selectByProjectId(projectId);
        long ownerCount = acls.stream().filter(a -> "owner".equals(a.getRole())).count();
        if (project.getCreatedByUserId().equals(memberUserId)) {
            if (ownerCount <= 1) {
                throw new BusinessException(400, "不能移除项目创建人（至少保留一名 owner）");
            }
        } else {
            AuthProjectAcl target = authProjectAclMapper.selectByProjectIdAndSysUserId(projectId, memberUserId);
            if (target != null && "owner".equals(target.getRole()) && ownerCount <= 1) {
                throw new BusinessException(400, "至少保留一名 owner");
            }
        }
        authProjectAclMapper.deleteByProjectIdAndSysUserId(projectId, memberUserId);
    }

    private boolean isProjectOwnerAcl(Long projectId, Long userId) {
        AuthProjectAcl acl = authProjectAclMapper.selectByProjectIdAndSysUserId(projectId, userId);
        return acl != null && "owner".equals(acl.getRole());
    }

    private static ProjectVO toVO(Project p) {
        String createdAtStr = null;
        if (p.getCreatedAt() != null) {
            createdAtStr = p.getCreatedAt().format(CREATED_AT_FORMAT);
        }
        ProjectVO vo = new ProjectVO();
        vo.setId(p.getId());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setStatus(p.getStatus());
        vo.setHasProcurement(Boolean.TRUE.equals(p.getHasProcurement()));
        vo.setCreatedAt(createdAtStr);
        return vo;
    }
}
