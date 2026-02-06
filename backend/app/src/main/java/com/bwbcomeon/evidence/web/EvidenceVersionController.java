package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.PageResult;
import com.bwbcomeon.evidence.dto.EvidenceListItemVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.exception.ForbiddenException;
import com.bwbcomeon.evidence.exception.UnauthorizedException;
import com.bwbcomeon.evidence.service.AuthService;
import com.bwbcomeon.evidence.service.EvidenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 证据版本控制器（下载）及全局证据列表查询
 */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceVersionController {
    private static final Logger logger = LoggerFactory.getLogger(EvidenceVersionController.class);

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private AuthService authService;

    /**
     * 全局证据分页列表（仅当前用户可见项目内证据，默认不含作废）
     * GET /api/evidence?page=&pageSize=&projectId=&status=&uploader=me|&recentDays=&fileCategory=&nameLike=
     */
    @GetMapping
    public Result<PageResult<EvidenceListItemVO>> listEvidence(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String uploader,
            @RequestParam(required = false) Integer recentDays,
            @RequestParam(required = false) String fileCategory,
            @RequestParam(required = false) String nameLike) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        PageResult<EvidenceListItemVO> data = evidenceService.pageEvidence(
                page, pageSize, projectId, status, uploader, recentDays, fileCategory, nameLike,
                user.getId(), user.getRoleCode());
        return Result.success(data);
    }

    /**
     * 证据详情（含 evidenceStatus）
     * GET /api/evidence/{id}
     */
    @GetMapping("/{id}")
    public Result<EvidenceListItemVO> getEvidence(
            HttpServletRequest request,
            @PathVariable Long id) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        EvidenceListItemVO data = evidenceService.getEvidenceById(id, user.getId(), user.getRoleCode());
        return Result.success(data);
    }

    /**
     * 证据状态流转：提交（DRAFT -> SUBMITTED）
     * POST /api/evidence/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public Result<Void> submitEvidence(
            HttpServletRequest request,
            @PathVariable Long id) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        evidenceService.submitEvidence(id, user.getId(), user.getRoleCode());
        return Result.success(null);
    }

    /**
     * 证据状态流转：归档（SUBMITTED -> ARCHIVED）
     * POST /api/evidence/{id}/archive
     */
    @PostMapping("/{id}/archive")
    public Result<Void> archiveEvidence(
            HttpServletRequest request,
            @PathVariable Long id) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        evidenceService.archiveEvidence(id, user.getId(), user.getRoleCode());
        return Result.success(null);
    }

    /**
     * 证据状态流转：作废（DRAFT/SUBMITTED -> INVALID），仅项目责任人可操作，请求体需传 invalidReason
     * POST /api/evidence/{id}/invalidate
     */
    @PostMapping("/{id}/invalidate")
    public Result<Void> invalidateEvidence(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        String invalidReason = body != null ? body.get("invalidReason") : null;
        com.bwbcomeon.evidence.dto.InvalidateAuditInfo info =
                evidenceService.invalidateEvidence(id, user.getId(), user.getRoleCode(), invalidReason);
        String detail = (invalidReason != null && !invalidReason.isBlank()) ? "作废原因: " + invalidReason : "证据作废";
        authService.recordAudit(request, "EVIDENCE_INVALIDATE", true, user.getId(), null, null, detail,
                "EVIDENCE", id, info.getProjectId(), info.getBeforeData(), info.getAfterData());
        return Result.success(null);
    }

    /**
     * 下载证据版本文件（预览/下载共用，按当前登录用户做项目权限校验）
     * GET /api/evidence/versions/{versionId}/download
     *
     * @param versionId 版本ID
     * @return 文件资源（附件形式）
     */
    @GetMapping("/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadVersionFile(
            HttpServletRequest request,
            @PathVariable Long versionId) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            throw new UnauthorizedException("未登录");
        }
        logger.info("Download version file request: versionId={}, userId={}", versionId, user.getId());
        Resource resource = evidenceService.downloadVersionFile(versionId, user.getId());
        
        // 获取原始文件名和Content-Type
        String originalFilename = evidenceService.getVersionOriginalFilename(versionId);
        String contentType = evidenceService.getVersionContentType(versionId);
        
        // 处理文件名编码（支持中文文件名）
        String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        
        // 构建响应头
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                   "attachment; filename=\"" + originalFilename + "\"; filename*=UTF-8''" + encodedFilename);
        
        // 设置Content-Type
        if (contentType != null && !contentType.isEmpty()) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        
        logger.info("Download version file response: versionId={}, filename={}, contentType={}", 
                   versionId, originalFilename, contentType);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
