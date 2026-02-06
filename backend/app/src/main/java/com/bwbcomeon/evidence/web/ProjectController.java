package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AddProjectMemberRequest;
import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.CreateProjectRequest;
import com.bwbcomeon.evidence.dto.ProjectMemberVO;
import com.bwbcomeon.evidence.dto.ProjectImportResult;
import com.bwbcomeon.evidence.dto.ProjectVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.service.EvidenceService;
import com.bwbcomeon.evidence.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
        List<ProjectVO> list = projectService.listVisibleProjects(user.getId(), user.getRoleCode());
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
        ProjectVO vo = projectService.getProjectDetail(projectId, user.getId(), user.getRoleCode());
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
        logger.info("Create project request: code={}, name={}, userId={}", body.getCode(), body.getName(), user.getId());
        ProjectVO vo = projectService.createProject(
                user.getId(),
                body.getCode(),
                body.getName(),
                body.getDescription());
        return Result.success(vo);
    }

    /**
     * 下载项目导入模板（xlsx，表头：项目令号、项目名称、项目描述）
     * GET /api/projects/import/template
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("项目");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("项目令号");
            header.createCell(1).setCellValue("项目名称");
            header.createCell(2).setCellValue("项目描述");
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("PROJ-001");
            example.createCell(1).setCellValue("示例项目");
            example.createCell(2).setCellValue("示例描述");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "项目导入模板.xlsx");
            return ResponseEntity.ok().headers(headers).body(out.toByteArray());
        } catch (Exception e) {
            logger.warn("Build import template failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * PMO Excel 批量导入项目（仅 SYSTEM_ADMIN/PMO）
     * POST /api/projects/import  body: multipart file (xlsx)，模板列：项目令号、项目名称、项目描述
     */
    @PostMapping("/import")
    public Result<ProjectImportResult> importProjects(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        if (file == null || file.isEmpty()) return Result.error(400, "请选择文件");
        try {
            ProjectImportResult result = projectService.importProjectsFromExcel(file.getInputStream(), user.getId(), user.getRoleCode());
            return Result.success(result);
        } catch (Exception e) {
            logger.warn("Import projects failed", e);
            return Result.error(400, e.getMessage() != null ? e.getMessage() : "导入失败");
        }
    }

    /**
     * 项目成员列表（可见项目即可查看）
     * GET /api/projects/{projectId}/members
     */
    @GetMapping("/{projectId}/members")
    public Result<List<ProjectMemberVO>> listMembers(
            HttpServletRequest request,
            @PathVariable Long projectId) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        List<ProjectMemberVO> list = projectService.listMembers(projectId, user.getId(), user.getRoleCode());
        return Result.success(list);
    }

    /**
     * 添加或调整项目成员（仅 owner 或 SYSTEM_ADMIN）
     * POST /api/projects/{projectId}/members  body: { userId(sys_user.id), role(owner|editor|viewer) }
     */
    @PostMapping("/{projectId}/members")
    public Result<Void> addOrUpdateMember(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @RequestBody AddProjectMemberRequest body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        projectService.addOrUpdateMember(projectId, user.getId(), user.getRoleCode(), body);
        return Result.success(null);
    }

    /**
     * 移除项目成员（仅 owner 或 SYSTEM_ADMIN）
     * DELETE /api/projects/{projectId}/members/{userId}
     */
    @DeleteMapping("/{projectId}/members/{userId}")
    public Result<Void> removeMember(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @PathVariable Long userId) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        projectService.removeMember(projectId, userId, user.getId(), user.getRoleCode());
        return Result.success(null);
    }
}
