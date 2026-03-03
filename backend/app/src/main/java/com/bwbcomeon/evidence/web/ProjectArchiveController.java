package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.ArchiveApplicationVO;
import com.bwbcomeon.evidence.dto.ArchiveRejectRequest;
import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.service.ProjectArchiveService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 归档审批流接口：申请、通过、退回
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectArchiveController {

    @Autowired
    private ProjectArchiveService projectArchiveService;

    @PostMapping("/{projectId}/archive-apply")
    public Result<ArchiveApplicationVO> archiveApply(HttpServletRequest request, @PathVariable Long projectId) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        ArchiveApplicationVO vo = projectArchiveService.apply(projectId, user.getId(), user.getRoleCode());
        return Result.success(vo);
    }

    @PostMapping("/{projectId}/archive-approve")
    public Result<Void> archiveApprove(HttpServletRequest request, @PathVariable Long projectId) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        projectArchiveService.approve(projectId, user.getId(), user.getRoleCode());
        return Result.success();
    }

    @PostMapping("/{projectId}/archive-reject")
    public Result<Void> archiveReject(HttpServletRequest request,
                                      @PathVariable Long projectId,
                                      @RequestBody @Valid ArchiveRejectRequest body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        projectArchiveService.reject(projectId, body, user.getId(), user.getRoleCode());
        return Result.success();
    }
}
