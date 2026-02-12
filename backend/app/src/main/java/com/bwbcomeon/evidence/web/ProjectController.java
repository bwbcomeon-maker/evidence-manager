package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AddProjectMemberRequest;
import com.bwbcomeon.evidence.dto.ArchiveBlockVO;
import com.bwbcomeon.evidence.dto.ArchiveResult;
import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.BatchAddProjectMembersRequest;
import com.bwbcomeon.evidence.dto.BatchAssignResult;
import com.bwbcomeon.evidence.dto.BatchAssignUserToProjectsRequest;
import com.bwbcomeon.evidence.dto.CreateProjectRequest;
import com.bwbcomeon.evidence.dto.EvidenceListItemVO;
import com.bwbcomeon.evidence.dto.ProjectMemberVO;
import com.bwbcomeon.evidence.dto.ProjectImportResult;
import com.bwbcomeon.evidence.dto.ProjectVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.dto.StageCompleteResult;
import com.bwbcomeon.evidence.dto.StageProgressVO;
import com.bwbcomeon.evidence.service.EvidenceService;
import com.bwbcomeon.evidence.service.ProjectService;
import com.bwbcomeon.evidence.service.StageProgressService;
import com.bwbcomeon.evidence.util.PermissionUtil;
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
    private StageProgressService stageProgressService;

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private PermissionUtil permissionUtil;

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
        String roleCode = user.getRoleCode();
        if (roleCode == null || (!"SYSTEM_ADMIN".equals(roleCode) && !"PMO".equals(roleCode))) {
            return Result.error(403, "仅管理员或 PMO 可创建项目");
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
     * 批量将一人分配至多个项目（仅 PMO / 系统管理员）
     * POST /api/projects/batch-members  body: { userId, projectIds: [id,...], role?: "owner"|"editor"|"viewer" }
     * 响应：{ successCount, failCount, errors: ["项目1: ...", ...] }
     */
    @PostMapping("/batch-members")
    public Result<BatchAssignResult> batchAssignUserToProjects(
            HttpServletRequest request,
            @RequestBody @Valid BatchAssignUserToProjectsRequest body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        BatchAssignResult result = projectService.batchAssignUserToProjects(body, user.getId(), user.getRoleCode());
        return Result.success(result);
    }

    /**
     * 批量为一个项目添加/调整多名成员（含项目经理 owner）
     * POST /api/projects/{projectId}/members/batch  body: { members: [ { userId, role }, ... ] }
     * 响应：{ successCount, failCount, errors }
     */
    @PostMapping("/{projectId}/members/batch")
    public Result<BatchAssignResult> batchAddProjectMembers(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @RequestBody @Valid BatchAddProjectMembersRequest body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        BatchAssignResult result = projectService.batchAddProjectMembers(projectId, body, user.getId(), user.getRoleCode());
        return Result.success(result);
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

    /**
     * 阶段进度（唯一事实源）
     * GET /api/projects/{projectId}/stage-progress
     */
    @GetMapping("/{projectId}/stage-progress")
    public Result<StageProgressVO> getStageProgress(
            HttpServletRequest request,
            @PathVariable Long projectId) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(user.getId(), user.getRoleCode());
        if (!visibleIds.contains(projectId)) {
            return Result.error(403, "无权限访问该项目");
        }
        StageProgressVO vo = stageProgressService.computeStageProgress(projectId);
        if (vo == null) {
            return Result.error(404, "项目不存在");
        }
        return Result.success(vo);
    }

    /**
     * 按阶段+模板项证据实例列表
     * GET /api/projects/{projectId}/stages/{stageCode}/evidence-types/{evidenceTypeCode}/evidences
     */
    @GetMapping("/{projectId}/stages/{stageCode}/evidence-types/{evidenceTypeCode}/evidences")
    public Result<List<EvidenceListItemVO>> listEvidencesByStageAndType(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @PathVariable String stageCode,
            @PathVariable String evidenceTypeCode) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        List<EvidenceListItemVO> list = evidenceService.listEvidencesByStageAndType(
                projectId, stageCode, evidenceTypeCode, user.getId(), user.getRoleCode());
        return Result.success(list);
    }

    /**
     * 标记阶段完成（门禁失败返回 400 + missingItems + message）
     * POST /api/projects/{projectId}/stages/{stageCode}/complete
     */
    @PostMapping("/{projectId}/stages/{stageCode}/complete")
    public Result<StageCompleteResult> completeStage(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @PathVariable String stageCode) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        permissionUtil.checkCanUpload(projectId, user.getId(), user.getRoleCode());
        StageCompleteResult result = stageProgressService.completeStage(projectId, stageCode);
        if (!result.isSuccess()) {
            return Result.error(400, result.getMessage(), result);
        }
        return Result.success(result);
    }

    /**
     * 项目归档（门禁失败返回 400 + keyMissing + archiveBlockReason + blockedByStages + blockedByRequiredItems）
     * POST /api/projects/{projectId}/archive
     */
    @PostMapping("/{projectId}/archive")
    public Result<?> archiveProject(
            HttpServletRequest request,
            @PathVariable Long projectId) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        ArchiveResult result = projectService.archive(projectId, user.getId(), user.getRoleCode());
        if (!result.isSuccess()) {
            ArchiveBlockVO block = result.getBlock();
            return Result.error(400, block != null ? block.getArchiveBlockReason() : "不满足归档条件", block);
        }
        return Result.success(null);
    }
}
