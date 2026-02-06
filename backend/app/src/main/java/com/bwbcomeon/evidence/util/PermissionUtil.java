package com.bwbcomeon.evidence.util;

import com.bwbcomeon.evidence.dto.PermissionBits;
import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.AuthProjectAclMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 权限校验工具类
 */
@Component
public class PermissionUtil {
    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AuthProjectAclMapper authProjectAclMapper;

    /**
     * 检查用户是否有项目权限（ADMIN/OWNER/EDITOR可上传）
     * 
     * @param projectId 项目ID
     * @param userId 用户ID
     * @param requireUploadPermission 是否需要上传权限（EDITOR及以上）
     * @throws BusinessException 如果权限不足
     */
    public void checkProjectPermission(Long projectId, Long userId, boolean requireUploadPermission) {
        // 检查项目是否存在
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "Project not found");
        }

        // 检查用户是否是项目创建者（owner权限）
        if (userId != null && userId.equals(project.getCreatedByUserId())) {
            return; // 项目创建者拥有所有权限
        }

        // 检查用户是否是项目成员
        AuthProjectAcl acl = authProjectAclMapper.selectByProjectIdAndSysUserId(projectId, userId);
        if (acl == null) {
            throw new BusinessException(403, "No permission to access this project");
        }

        // 如果需要上传权限，检查角色
        if (requireUploadPermission) {
            String role = acl.getRole();
            if ("viewer".equals(role)) {
                throw new BusinessException(403, "Viewer role cannot upload evidence");
            }
            // owner 和 editor 都可以上传
        }
    }

    /**
     * 检查用户是否有项目访问权限
     */
    public void checkProjectAccess(Long projectId, Long userId) {
        checkProjectPermission(projectId, userId, false);
    }

    /**
     * 检查用户是否有项目上传权限
     */
    public void checkProjectUploadPermission(Long projectId, Long userId) {
        checkProjectPermission(projectId, userId, true);
    }

    /**
     * 检查用户是否是ADMIN（当前未实现ADMIN角色，返回false）
     * TODO: 后续需要实现ADMIN角色检查
     */
    public boolean isAdmin(Long userId) {
        // TODO: 实现ADMIN角色检查逻辑
        return false;
    }

    /**
     * 检查当前用户是否有权作废该项目下的证据（仅项目责任人：SYSTEM_ADMIN / 项目创建人 / ACL owner）
     *
     * @param projectId 项目ID
     * @param userId    当前用户 sys_user.id
     * @param roleCode  sys_user.role_code（SYSTEM_ADMIN 则直接通过）
     * @throws BusinessException 无权限时 403
     */
    public void checkCanInvalidate(Long projectId, Long userId, String roleCode) {
        checkCanArchive(projectId, userId, roleCode);
    }

    /**
     * 检查当前用户是否有权归档该项目下的证据（与作废同源：仅项目责任人）
     * SYSTEM_ADMIN / 项目创建人 / ACL owner；PMO 不自动放行。
     *
     * @param projectId 项目ID
     * @param userId    当前用户 sys_user.id
     * @param roleCode  sys_user.role_code（SYSTEM_ADMIN 则直接通过）
     * @throws BusinessException 无权限时 403
     */
    public void checkCanArchive(Long projectId, Long userId, String roleCode) {
        if (roleCode != null && "SYSTEM_ADMIN".equals(roleCode)) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (userId != null && userId.equals(project.getCreatedByUserId())) {
            return;
        }
        AuthProjectAcl acl = authProjectAclMapper.selectByProjectIdAndSysUserId(projectId, userId);
        if (acl != null && "owner".equals(acl.getRole())) {
            return;
        }
        throw new BusinessException(403, "仅项目责任人可归档证据");
    }

    /**
     * 检查当前用户是否可管理项目成员（超级管理员 SYSTEM_ADMIN、PMO、本项目负责人 owner）
     */
    public void checkCanManageMembers(Long projectId, Long userId, String roleCode) {
        if (roleCode != null && ("SYSTEM_ADMIN".equals(roleCode) || "PMO".equals(roleCode))) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (userId != null && userId.equals(project.getCreatedByUserId())) {
            return;
        }
        AuthProjectAcl acl = authProjectAclMapper.selectByProjectIdAndSysUserId(projectId, userId);
        if (acl != null && "owner".equals(acl.getRole())) {
            return;
        }
        throw new BusinessException(403, "仅项目责任人可管理成员");
    }

    /**
     * 检查当前用户是否可上传证据（SYSTEM_ADMIN / 项目创建人 / ACL owner 或 editor；viewer 不可）
     */
    public void checkCanUpload(Long projectId, Long userId, String roleCode) {
        if (roleCode != null && "SYSTEM_ADMIN".equals(roleCode)) {
            return;
        }
        checkProjectPermission(projectId, userId, true);
    }

    /**
     * 检查当前用户是否可提交证据（与上传权限一致：owner/editor 或 SYSTEM_ADMIN）
     */
    public void checkCanSubmit(Long projectId, Long userId, String roleCode) {
        checkCanUpload(projectId, userId, roleCode);
    }

    /**
     * V1 统一计算项目内权限位（与 checkCan* 同源，供接口返回 permissions）
     * SYSTEM_ADMIN 全 true；AUDITOR/PROJECT_AUDITOR 全 false；
     * PMO 仅 canManageMembers 默认 true，证据位仅当该项目 created_by/owner/editor 时按角色给；
     * 其他按 created_by/ACL owner/editor/viewer 计算。
     */
    public PermissionBits computeProjectPermissionBits(Long projectId, Long userId, String roleCode) {
        if (roleCode != null && "SYSTEM_ADMIN".equals(roleCode)) {
            return PermissionBits.all(true);
        }
        if (roleCode != null && ("AUDITOR".equals(roleCode) || "PROJECT_AUDITOR".equals(roleCode))) {
            return PermissionBits.all(false);
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return PermissionBits.all(false);
        }
        boolean isCreatedBy = userId != null && userId.equals(project.getCreatedByUserId());
        AuthProjectAcl acl = authProjectAclMapper.selectByProjectIdAndSysUserId(projectId, userId);
        String projectRole = isCreatedBy ? "owner" : (acl != null ? acl.getRole() : null);

        boolean canManageMembers;
        boolean canUpload, canSubmit, canArchive, canInvalidate;
        if (roleCode != null && "PMO".equals(roleCode)) {
            canManageMembers = true;
            canUpload = projectRole != null && !"viewer".equals(projectRole);
            canSubmit = canUpload;
            canArchive = "owner".equals(projectRole);
            canInvalidate = canArchive;
        } else {
            canManageMembers = "owner".equals(projectRole);
            canUpload = projectRole != null && !"viewer".equals(projectRole);
            canSubmit = canUpload;
            canArchive = "owner".equals(projectRole);
            canInvalidate = canArchive;
        }
        return new PermissionBits(canUpload, canSubmit, canArchive, canInvalidate, canManageMembers);
    }
}
