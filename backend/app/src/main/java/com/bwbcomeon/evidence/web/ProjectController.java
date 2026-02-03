package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.CreateProjectRequest;
import com.bwbcomeon.evidence.dto.ProjectVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.service.EvidenceService;
import com.bwbcomeon.evidence.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 项目控制器（P0-3：创建项目）
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EvidenceService evidenceService;

    /**
     * 创建项目
     * POST /api/projects
     * 请求体：name(必填), description(可选)
     * 响应：新建项目的 id + 基本字段
     */
    @PostMapping
    public Result<ProjectVO> createProject(
            HttpServletRequest request,
            @RequestBody @Valid CreateProjectRequest body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        UUID currentUserId = evidenceService.resolveCreatedByUuid(user.getUsername());
        if (currentUserId == null) {
            return Result.error(403, "无法解析当前用户");
        }
        logger.info("Create project request: name={}, userId={}", body.getName(), currentUserId);
        ProjectVO vo = projectService.createProject(
                currentUserId,
                body.getName(),
                body.getDescription());
        return Result.success(vo);
    }
}
