package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.PageResult;
import com.bwbcomeon.evidence.dto.EvidenceListItemVO;
import com.bwbcomeon.evidence.dto.EvidenceSearchResultVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.dto.VersionDownloadResult;
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
     * 全局证据搜索（仅当前用户可见项目内，按关键字匹配证据标题或上传人姓名/账号）
     * GET /api/evidence/global-search?keyword=&page=1&pageSize=10
     */
    @GetMapping("/global-search")
    public Result<PageResult<EvidenceSearchResultVO>> globalSearch(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        PageResult<EvidenceSearchResultVO> data = evidenceService.globalSearchEvidence(
                keyword, page, pageSize, user.getId(), user.getRoleCode());
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
     * 草稿证据物理删除（仅 DRAFT 可删；已提交/已归档不可物理删除，只能作废）
     * DELETE /api/evidence/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteEvidence(
            HttpServletRequest request,
            @PathVariable Long id) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) return Result.error(401, "未登录");
        evidenceService.deleteEvidence(id, user.getId(), user.getRoleCode());
        return Result.success(null);
    }

    /**
     * 证据状态流转：作废（仅 SUBMITTED -> INVALID），仅项目责任人可操作，请求体需传 invalidReason；草稿不可作废，只能删除或提交
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
     * 证据版本文件访问（预览与下载共用同一接口，通过参数区分语义）
     * GET /api/evidence/versions/{versionId}/download
     *
     * <p><b>预览与下载语义：</b></p>
     * <ul>
     *   <li><b>预览</b>：调用时传 {@code preview=true}，返回 Content-Disposition: inline，供页面内嵌展示；
     *       图片类型默认返回水印图（无水印则回退原图），非图片返回原文件。</li>
     *   <li><b>下载</b>：不传或 {@code preview=false}，返回 Content-Disposition: attachment，触发浏览器下载；
     *       与预览使用相同文件内容（同上，图片默认水印优先）。</li>
     *   <li><b>原图</b>：仅当配置 {@code evidence.image.original-access-enabled=true} 且当前用户角色为
     *       SYSTEM_ADMIN 或 PMO 时，传 {@code variant=ORIGINAL} 可获取原图；否则返回 403。</li>
     * </ul>
     *
     * @param versionId 版本ID
     * @param preview true 表示预览（inline），false 或不传表示下载（attachment）
     * @param variant WATERMARKED（默认）| ORIGINAL（需配置+角色权限）
     * @return 文件资源（附件或内联）
     */
    @GetMapping("/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadVersionFile(
            HttpServletRequest request,
            @PathVariable Long versionId,
            @RequestParam(required = false) Boolean preview,
            @RequestParam(required = false) String variant) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            throw new UnauthorizedException("未登录");
        }
        logger.info("Download version file request: versionId={}, userId={}, preview={}, variant={}", versionId, user.getId(), preview, variant);
        VersionDownloadResult result = evidenceService.downloadVersionFile(versionId, user.getId(), user.getRoleCode(), variant);
        Resource resource = result.getResource();
        String filename = result.getFilename();
        String contentType = result.getContentType();
        
        // 处理文件名编码（支持中文文件名）
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        
        // 构建响应头：预览时使用 inline 以便 iframe 内展示（Word/Excel/PPT 等）
        HttpHeaders headers = new HttpHeaders();
        boolean inline = Boolean.TRUE.equals(preview) && result.isInlinePreviewAllowed();
        String disposition = inline
                ? "inline; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename
                : "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename;
        headers.add(HttpHeaders.CONTENT_DISPOSITION, disposition);
        headers.add("X-Content-Type-Options", "nosniff");
        
        // 设置Content-Type
        if (contentType != null && !contentType.isEmpty()) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        
        logger.info("Download version file response: versionId={}, filename={}, contentType={}",
                   versionId, filename, contentType);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
