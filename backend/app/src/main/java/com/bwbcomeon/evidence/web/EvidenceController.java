package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.EvidenceListItemVO;
import com.bwbcomeon.evidence.dto.EvidenceResponse;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.service.EvidenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 证据控制器
 */
@RestController
@RequestMapping("/api/projects")
public class EvidenceController {
    private static final Logger logger = LoggerFactory.getLogger(EvidenceController.class);

    @Autowired
    private EvidenceService evidenceService;

    /**
     * 上传证据
     * POST /api/projects/{projectId}/evidences
     * 
     * @param projectId 项目ID
     * @param name 证据名称
     * @param type 证据类型
     * @param remark 备注（可选）
     * @param file 文件
     * @return 证据响应
     */
    @PostMapping("/{projectId}/evidences")
    public Result<EvidenceResponse> uploadEvidence(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @RequestParam("name") String name,
            @RequestParam("type") String type,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam("file") MultipartFile file) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        logger.info("Upload evidence request: projectId={}, name={}, type={}, fileName={}, userId={}",
                   projectId, name, type, file.getOriginalFilename(), user.getId());
        EvidenceResponse response = evidenceService.uploadEvidence(
            projectId, name, type, remark, file, user.getId(), user.getRoleCode());
        return Result.success(response);
    }

    /**
     * 按项目查询证据列表
     * GET /api/projects/{projectId}/evidences
     * 
     * @param projectId 项目ID
     * @param nameLike 证据名称模糊匹配（可选）
     * @param status 证据状态（可选）
     * @param bizType 业务证据类型（可选，如PLAN/REPORT/MINUTES/TEST/ACCEPTANCE/OTHER）
     * @param contentType 文件类型（MIME类型，可选，如application/pdf）
     * @return 证据列表
     */
    @GetMapping("/{projectId}/evidences")
    public Result<List<EvidenceListItemVO>> listEvidences(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @RequestParam(value = "nameLike", required = false) String nameLike,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "contentType", required = false) String contentType) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        logger.info("List evidences request: projectId={}, nameLike={}, status={}, bizType={}, contentType={}, userId={}",
                   projectId, nameLike, status, bizType, contentType, user.getId());
        List<EvidenceListItemVO> result = evidenceService.listEvidences(
                projectId, nameLike, status, bizType, contentType, user.getId(), user.getRoleCode());
        return Result.success(result);
    }
}
