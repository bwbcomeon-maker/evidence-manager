package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.AddProjectMemberRequest;
import com.bwbcomeon.evidence.dto.PermissionBits;
import com.bwbcomeon.evidence.dto.ProjectImportResult;
import com.bwbcomeon.evidence.dto.ProjectVO;
import com.bwbcomeon.evidence.dto.ProjectMemberVO;
import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import com.bwbcomeon.evidence.entity.AuthUser;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.AuthProjectAclMapper;
import com.bwbcomeon.evidence.mapper.AuthUserMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 项目服务（P0-3 创建项目；P0-2 列表/详情只读）
 */
@Service
public class ProjectService {

    private static final String ROLE_OWNER = "owner";
    private static final String STATUS_ACTIVE = "active";
    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AuthProjectAclMapper authProjectAclMapper;

    @Autowired
    private AuthUserMapper authUserMapper;

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private PermissionUtil permissionUtil;

    private static final Set<String> ACL_ROLES = Set.of("owner", "editor", "viewer");
    private static final int IMPORT_MAX_ROWS = 500;

    /**
     * PMO Excel 批量导入项目（最小版）：模板列 项目令号、项目名称、项目描述；按 code upsert；仅 SYSTEM_ADMIN/PMO 可调。
     */
    public ProjectImportResult importProjectsFromExcel(InputStream inputStream, UUID operatorUserId, String roleCode) {
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
                        project.setCreatedBy(operatorUserId);
                        projectMapper.insert(project);
                        AuthProjectAcl acl = new AuthProjectAcl();
                        acl.setProjectId(project.getId());
                        acl.setUserId(operatorUserId);
                        acl.setRole(ROLE_OWNER);
                        authProjectAclMapper.insert(acl);
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
     * @param userId      当前用户 UUID（auth_user.id）
     * @param code        项目令号（必填，唯一）
     * @param name        项目名称（必填）
     * @param description 项目描述（可选）
     * @return 新建项目的基本信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO createProject(UUID userId, String code, String name, String description) {
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
        project.setCreatedBy(userId);
        projectMapper.insert(project);

        AuthProjectAcl acl = new AuthProjectAcl();
        acl.setProjectId(project.getId());
        acl.setUserId(userId);
        acl.setRole(ROLE_OWNER);
        authProjectAclMapper.insert(acl);

        return toVO(project);
    }

    /**
     * 当前用户可见的项目列表（SYSTEM_ADMIN 全部；普通用户：自己创建的 + ACL 可见）
     */
    public List<ProjectVO> listVisibleProjects(String username, String roleCode) {
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(username, roleCode);
        if (visibleIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Project> projects = projectMapper.selectByIds(visibleIds);
        List<ProjectVO> result = new ArrayList<>(projects.size());
        for (Project p : projects) {
            result.add(toVO(p));
        }
        return result;
    }

    /**
     * 项目详情（做可见性校验，不可见返回 403），并返回当前用户是否可作废证据 canInvalidate
     */
    public ProjectVO getProjectDetail(Long projectId, String username, String roleCode) {
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(username, roleCode);
        if (!visibleIds.contains(projectId)) {
            throw new BusinessException(403, "无权限访问该项目");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        ProjectVO vo = toVO(project);
        UUID userId = evidenceService.resolveCreatedByUuid(username);
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(projectId, userId, roleCode);
        vo.setPermissions(bits);
        vo.setCanInvalidate(Boolean.TRUE.equals(bits.getCanInvalidate()));
        vo.setCanManageMembers(Boolean.TRUE.equals(bits.getCanManageMembers()));
        vo.setCanUpload(Boolean.TRUE.equals(bits.getCanUpload()));
        UUID pmUserId = resolveCurrentPmUserId(projectId, project);
        if (pmUserId != null) {
            vo.setCurrentPmUserId(pmUserId.toString());
            AuthUser pmUser = authUserMapper.selectById(pmUserId);
            vo.setCurrentPmDisplayName(pmUser != null ? pmUser.getDisplayName() : null);
        }
        return vo;
    }

    /** 当前项目经理：ACL 中 role=owner 的用户（V1 唯一）；若无则取 project.created_by */
    private UUID resolveCurrentPmUserId(Long projectId, Project project) {
        List<AuthProjectAcl> acls = authProjectAclMapper.selectByProjectId(projectId);
        for (AuthProjectAcl acl : acls) {
            if (ROLE_OWNER.equals(acl.getRole())) {
                return acl.getUserId();
            }
        }
        return project.getCreatedBy();
    }

    /**
     * 项目成员列表（可见项目即可查看；成员来自 auth_project_acl + auth_user）
     */
    public List<ProjectMemberVO> listMembers(Long projectId, String username, String roleCode) {
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(username, roleCode);
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
        List<UUID> userIds = acls.stream().map(AuthProjectAcl::getUserId).distinct().collect(Collectors.toList());
        List<AuthUser> users = authUserMapper.selectByIds(userIds);
        java.util.Map<UUID, AuthUser> userMap = users.stream().collect(Collectors.toMap(AuthUser::getId, u -> u));
        UUID currentAuthUserId = evidenceService.resolveCreatedByUuid(username);
        List<ProjectMemberVO> result = new ArrayList<>(acls.size());
        for (AuthProjectAcl acl : acls) {
            AuthUser u = userMap.get(acl.getUserId());
            ProjectMemberVO vo = new ProjectMemberVO(
                    acl.getUserId(),
                    acl.getRole(),
                    u != null ? u.getUsername() : null,
                    u != null ? u.getDisplayName() : null,
                    null
            );
            vo.setIsCurrentUser(currentAuthUserId != null && currentAuthUserId.equals(acl.getUserId()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 添加或调整项目成员（SYSTEM_ADMIN/PMO/项目负责人）。
     * V1：每项目最多一个 owner；分配 PM = 事务内删旧 owner + 增新 owner。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdateMember(Long projectId, UUID operatorUserId, String roleCode, AddProjectMemberRequest body) {
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
        if (authUserMapper.selectById(body.getUserId()) == null) {
            throw new BusinessException(400, "用户不存在");
        }
        UUID targetUserId = body.getUserId();
        AuthProjectAcl existing = authProjectAclMapper.selectByProjectIdAndUserId(projectId, targetUserId);

        if (ROLE_OWNER.equals(role)) {
            // 每项目唯一 owner：先移除其他所有 owner，再设目标用户为 owner（同一事务）
            List<AuthProjectAcl> acls = authProjectAclMapper.selectByProjectId(projectId);
            for (AuthProjectAcl acl : acls) {
                if (ROLE_OWNER.equals(acl.getRole()) && !acl.getUserId().equals(targetUserId)) {
                    authProjectAclMapper.deleteByProjectIdAndUserId(projectId, acl.getUserId());
                }
            }
        }

        if (existing != null) {
            existing.setRole(role);
            authProjectAclMapper.update(existing);
        } else {
            AuthProjectAcl acl = new AuthProjectAcl();
            acl.setProjectId(projectId);
            acl.setUserId(targetUserId);
            acl.setRole(role);
            authProjectAclMapper.insert(acl);
        }
    }

    /**
     * 移除项目成员（仅 owner 或 SYSTEM_ADMIN）；不允许移除最后一个 owner
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long projectId, UUID memberUserId, UUID operatorUserId, String roleCode) {
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
        if (project.getCreatedBy().equals(memberUserId)) {
            if (ownerCount <= 1) {
                throw new BusinessException(400, "不能移除项目创建人（至少保留一名 owner）");
            }
        } else {
            AuthProjectAcl target = authProjectAclMapper.selectByProjectIdAndUserId(projectId, memberUserId);
            if (target != null && "owner".equals(target.getRole()) && ownerCount <= 1) {
                throw new BusinessException(400, "至少保留一名 owner");
            }
        }
        authProjectAclMapper.deleteByProjectIdAndUserId(projectId, memberUserId);
    }

    private boolean isProjectOwnerAcl(Long projectId, UUID userId) {
        AuthProjectAcl acl = authProjectAclMapper.selectByProjectIdAndUserId(projectId, userId);
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
        vo.setCreatedAt(createdAtStr);
        return vo;
    }
}
