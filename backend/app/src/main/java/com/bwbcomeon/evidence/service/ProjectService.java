package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.ProjectVO;
import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.mapper.AuthProjectAclMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 项目服务（P0-3 最小可用：创建项目）
 */
@Service
public class ProjectService {

    private static final String ROLE_OWNER = "owner";
    private static final String STATUS_ACTIVE = "active";

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AuthProjectAclMapper authProjectAclMapper;

    /**
     * 创建项目，并写入一条 owner ACL
     *
     * @param userId      当前用户 UUID（auth_user.id）
     * @param name        项目名称（必填）
     * @param description 项目描述（可选）
     * @return 新建项目的基本信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO createProject(UUID userId, String name, String description) {
        String code = "P-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Project project = new Project();
        project.setCode(code);
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

    private static ProjectVO toVO(Project p) {
        return new ProjectVO(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getStatus()
        );
    }
}
