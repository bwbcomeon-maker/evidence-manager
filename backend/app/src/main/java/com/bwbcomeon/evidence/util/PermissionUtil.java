package com.bwbcomeon.evidence.util;

import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.AuthProjectAclMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
    public void checkProjectPermission(Long projectId, UUID userId, boolean requireUploadPermission) {
        // 检查项目是否存在
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "Project not found");
        }

        // 检查用户是否是项目创建者（owner权限）
        if (project.getCreatedBy().equals(userId)) {
            return; // 项目创建者拥有所有权限
        }

        // 检查用户是否是项目成员
        AuthProjectAcl acl = authProjectAclMapper.selectByProjectIdAndUserId(projectId, userId);
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
    public void checkProjectAccess(Long projectId, UUID userId) {
        checkProjectPermission(projectId, userId, false);
    }

    /**
     * 检查用户是否有项目上传权限
     */
    public void checkProjectUploadPermission(Long projectId, UUID userId) {
        checkProjectPermission(projectId, userId, true);
    }

    /**
     * 检查用户是否是ADMIN（当前未实现ADMIN角色，返回false）
     * TODO: 后续需要实现ADMIN角色检查
     */
    public boolean isAdmin(UUID userId) {
        // TODO: 实现ADMIN角色检查逻辑
        return false;
    }
}
