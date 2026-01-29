package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.PageResult;
import com.bwbcomeon.evidence.dto.EvidenceListItemVO;
import com.bwbcomeon.evidence.dto.Result;
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
import java.util.UUID;

/**
 * 证据版本控制器（下载）及全局证据列表查询
 */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceVersionController {
    private static final Logger logger = LoggerFactory.getLogger(EvidenceVersionController.class);

    @Autowired
    private EvidenceService evidenceService;

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
                user.getId(), user.getUsername(), user.getRoleCode());
        return Result.success(data);
    }

    /**
     * 下载证据版本文件
     * GET /api/evidence/versions/{versionId}/download
     * 
     * @param versionId 版本ID
     * @return 文件资源（附件形式）
     */
    @GetMapping("/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadVersionFile(@PathVariable Long versionId) {
        // TODO: MVP阶段暂时使用固定用户ID，后续需要从认证信息中获取
        UUID currentUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        
        logger.info("Download version file request: versionId={}", versionId);
        
        // 获取文件资源
        Resource resource = evidenceService.downloadVersionFile(versionId, currentUserId);
        
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
