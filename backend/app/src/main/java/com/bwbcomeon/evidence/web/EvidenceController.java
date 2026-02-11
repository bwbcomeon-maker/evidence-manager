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
     * 必填参数：name, stageId, evidenceTypeCode, file
     */
    @PostMapping("/{projectId}/evidences")
    public Result<EvidenceResponse> uploadEvidence(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @RequestParam("name") String name,
            @RequestParam("stageId") Long stageId,
            @RequestParam("evidenceTypeCode") String evidenceTypeCode,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam("file") MultipartFile file) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        logger.info("Upload evidence request: projectId={}, name={}, stageId={}, evidenceTypeCode={}, fileName={}, userId={}",
                   projectId, name, stageId, evidenceTypeCode, file.getOriginalFilename(), user.getId());
        EvidenceResponse response = evidenceService.uploadEvidence(
            projectId, name, stageId, evidenceTypeCode, remark, file, user.getId(), user.getRoleCode());
        return Result.success(response);
    }

    /**
     * 按项目查询证据列表
     * GET /api/projects/{projectId}/evidences
     * 可选参数：nameLike, evidenceStatus, evidenceTypeCode, contentType
     */
    @GetMapping("/{projectId}/evidences")
    public Result<List<EvidenceListItemVO>> listEvidences(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @RequestParam(value = "nameLike", required = false) String nameLike,
            @RequestParam(value = "evidenceStatus", required = false) String evidenceStatus,
            @RequestParam(value = "evidenceTypeCode", required = false) String evidenceTypeCode,
            @RequestParam(value = "contentType", required = false) String contentType) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        logger.info("List evidences request: projectId={}, nameLike={}, evidenceStatus={}, evidenceTypeCode={}, contentType={}, userId={}",
                   projectId, nameLike, evidenceStatus, evidenceTypeCode, contentType, user.getId());
        List<EvidenceListItemVO> result = evidenceService.listEvidences(
                projectId, nameLike, evidenceStatus, evidenceTypeCode, contentType, user.getId(), user.getRoleCode());
        return Result.success(result);
    }
}
