package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.ProjectVO;
import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.AuthProjectAclMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private EvidenceService evidenceService;

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
     * 项目详情（做可见性校验，不可见返回 403）
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
        return toVO(project);
    }

    private static ProjectVO toVO(Project p) {
        String createdAtStr = null;
        if (p.getCreatedAt() != null) {
            createdAtStr = p.getCreatedAt().format(CREATED_AT_FORMAT);
        }
        return new ProjectVO(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getStatus(),
                createdAtStr
        );
    }
}
