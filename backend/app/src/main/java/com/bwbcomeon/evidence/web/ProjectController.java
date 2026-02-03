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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 项目控制器（P0-3 创建项目；P0-2 列表/详情只读）
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
     * 项目列表（当前用户可见）
     * GET /api/projects
     * 响应：ProjectVO 数组
     */
    @GetMapping
    public Result<List<ProjectVO>> listProjects(HttpServletRequest request) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        List<ProjectVO> list = projectService.listVisibleProjects(user.getUsername(), user.getRoleCode());
        return Result.success(list);
    }

    /**
     * 项目详情（可见性校验，不可见返回 403）
     * GET /api/projects/{projectId}
     */
    @GetMapping("/{projectId}")
    public Result<ProjectVO> getProjectDetail(
            HttpServletRequest request,
            @PathVariable Long projectId) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        ProjectVO vo = projectService.getProjectDetail(projectId, user.getUsername(), user.getRoleCode());
        return Result.success(vo);
    }

    /**
     * 创建项目
     * POST /api/projects
     * 请求体：code(项目令号,必填), name(必填), description(可选)
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
        logger.info("Create project request: code={}, name={}, userId={}", body.getCode(), body.getName(), currentUserId);
        ProjectVO vo = projectService.createProject(
                currentUserId,
                body.getCode(),
                body.getName(),
                body.getDescription());
        return Result.success(vo);
    }
}
