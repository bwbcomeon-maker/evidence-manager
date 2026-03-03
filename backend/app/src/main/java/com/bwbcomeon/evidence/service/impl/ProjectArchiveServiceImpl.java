package com.bwbcomeon.evidence.service.impl;

import com.bwbcomeon.evidence.dto.ArchiveApplicationVO;
import com.bwbcomeon.evidence.dto.ArchiveRejectRequest;
import com.bwbcomeon.evidence.dto.ArchiveResult;
import com.bwbcomeon.evidence.entity.ArchiveRejectEvidence;
import com.bwbcomeon.evidence.entity.Notification;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.entity.ProjectArchiveApplication;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.ArchiveRejectEvidenceMapper;
import com.bwbcomeon.evidence.mapper.NotificationMapper;
import com.bwbcomeon.evidence.mapper.ProjectArchiveApplicationMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import com.bwbcomeon.evidence.service.EvidenceService;
import com.bwbcomeon.evidence.service.ProjectArchiveService;
import com.bwbcomeon.evidence.service.ProjectService;
import com.bwbcomeon.evidence.service.StageProgressService;
import com.bwbcomeon.evidence.util.PermissionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 归档审批流服务实现
 */
@Service
public class ProjectArchiveServiceImpl implements ProjectArchiveService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";
    private static final String STATUS_RETURNED = "returned";
    private static final String APP_STATUS_PENDING = "PENDING_APPROVAL";
    private static final String APP_STATUS_APPROVED = "APPROVED";
    private static final String APP_STATUS_REJECTED = "REJECTED";
    private static final String NOTIFY_ARCHIVE_PENDING = "ARCHIVE_PENDING";
    private static final String NOTIFY_ARCHIVE_APPROVED = "ARCHIVE_APPROVED";
    private static final String NOTIFY_ARCHIVE_RETURNED = "ARCHIVE_RETURNED";

    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private ProjectArchiveApplicationMapper applicationMapper;
    @Autowired
    private ArchiveRejectEvidenceMapper rejectEvidenceMapper;
    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private PermissionUtil permissionUtil;
    @Autowired
    private EvidenceService evidenceService;
    @Autowired
    private StageProgressService stageProgressService;
    @Autowired
    private ProjectService projectService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArchiveApplicationVO apply(Long projectId, Long currentUserId, String roleCode) {
        permissionUtil.checkCanArchive(projectId, currentUserId, roleCode);
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(currentUserId, roleCode);
        if (!visibleIds.contains(projectId)) {
            throw new BusinessException(403, "无权限访问该项目");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (!STATUS_ACTIVE.equals(project.getStatus())) {
            throw new BusinessException(400, "当前项目状态不允许申请归档");
        }
        ProjectArchiveApplication pending = applicationMapper.selectPendingByProjectId(projectId);
        if (pending != null) {
            throw new BusinessException(400, "该项目已有待审批的归档申请");
        }
        com.bwbcomeon.evidence.dto.StageProgressVO progress = stageProgressService.computeStageProgress(projectId);
        if (progress == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (!progress.isCanArchive()) {
            throw new BusinessException(400, progress.getArchiveBlockReason() != null ? progress.getArchiveBlockReason() : "不满足归档条件");
        }

        ProjectArchiveApplication app = new ProjectArchiveApplication();
        app.setProjectId(projectId);
        app.setApplicantUserId(currentUserId);
        app.setStatus(APP_STATUS_PENDING);
        app.setSubmitTime(OffsetDateTime.now());
        applicationMapper.insert(app);

        projectMapper.updateStatusById(projectId, STATUS_PENDING_APPROVAL);

        List<Long> approverUserIds = sysUserMapper.selectIdsByRoleCodeIn(Arrays.asList("PMO", "SYSTEM_ADMIN"));
        String projectName = project.getName() != null ? project.getName() : "";
        String applicantName = resolveDisplayName(currentUserId);
        String title = "归档待审批：" + projectName;
        String body = "项目经理 " + applicantName + " 已提交归档申请，请审批。";
        String linkPath = "/projects/" + projectId + "?tab=evidence";
        for (Long userId : approverUserIds) {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(NOTIFY_ARCHIVE_PENDING);
            n.setTitle(title);
            n.setBody(body);
            n.setRelatedProjectId(projectId);
            n.setRelatedApplicationId(app.getId());
            n.setLinkPath(linkPath);
            notificationMapper.insert(n);
        }

        return toApplicationVO(app, project, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long projectId, Long currentUserId, String roleCode) {
        if (roleCode == null || (!"PMO".equals(roleCode) && !"SYSTEM_ADMIN".equals(roleCode))) {
            throw new BusinessException(403, "仅 PMO 或系统管理员可审批归档");
        }
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(currentUserId, roleCode);
        if (!visibleIds.contains(projectId)) {
            throw new BusinessException(403, "无权限访问该项目");
        }
        ProjectArchiveApplication app = applicationMapper.selectPendingByProjectId(projectId);
        if (app == null) {
            throw new BusinessException(404, "未找到待审批的归档申请");
        }

        OffsetDateTime now = OffsetDateTime.now();
        applicationMapper.updateStatusAndAudit(app.getId(), APP_STATUS_APPROVED, currentUserId, now, null, null);

        ArchiveResult result = projectService.archive(projectId, currentUserId, roleCode);
        if (!result.isSuccess()) {
            throw new BusinessException(400, result.getBlock() != null && result.getBlock().getArchiveBlockReason() != null
                    ? result.getBlock().getArchiveBlockReason() : "不满足归档条件");
        }

        Notification n = new Notification();
        n.setUserId(app.getApplicantUserId());
        n.setType(NOTIFY_ARCHIVE_APPROVED);
        n.setTitle("归档已通过");
        n.setBody("您申请归档的项目已审批通过。");
        n.setRelatedProjectId(projectId);
        n.setRelatedApplicationId(app.getId());
        n.setLinkPath("/projects/" + projectId + "?tab=evidence");
        notificationMapper.insert(n);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long projectId, ArchiveRejectRequest request, Long currentUserId, String roleCode) {
        if (request == null || request.getComment() == null || request.getComment().isBlank()) {
            throw new BusinessException(400, "退回意见不能为空");
        }
        if (roleCode == null || (!"PMO".equals(roleCode) && !"SYSTEM_ADMIN".equals(roleCode))) {
            throw new BusinessException(403, "仅 PMO 或系统管理员可退回归档申请");
        }
        List<Long> visibleIds = evidenceService.getVisibleProjectIds(currentUserId, roleCode);
        if (!visibleIds.contains(projectId)) {
            throw new BusinessException(403, "无权限访问该项目");
        }
        ProjectArchiveApplication app = applicationMapper.selectPendingByProjectId(projectId);
        if (app == null) {
            throw new BusinessException(404, "未找到待审批的归档申请");
        }

        rejectEvidenceMapper.deleteByApplicationId(app.getId());
        if (request.getEvidenceComments() != null && !request.getEvidenceComments().isEmpty()) {
            OffsetDateTime now = OffsetDateTime.now();
            for (ArchiveRejectRequest.EvidenceRejectItem item : request.getEvidenceComments()) {
                if (item == null || item.getEvidenceId() == null) continue;
                ArchiveRejectEvidence e = new ArchiveRejectEvidence();
                e.setApplicationId(app.getId());
                e.setEvidenceId(item.getEvidenceId());
                e.setRejectComment(item.getComment());
                e.setCreatedBy(currentUserId);
                e.setCreatedAt(now);
                rejectEvidenceMapper.insert(e);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        applicationMapper.updateStatusAndAudit(app.getId(), APP_STATUS_REJECTED, null, null, now, request.getComment().trim());
        projectMapper.updateStatusById(projectId, STATUS_RETURNED);

        Notification n = new Notification();
        n.setUserId(app.getApplicantUserId());
        n.setType(NOTIFY_ARCHIVE_RETURNED);
        n.setTitle("归档申请已退回");
        n.setBody("您的归档申请已被退回：" + request.getComment().trim());
        n.setRelatedProjectId(projectId);
        n.setRelatedApplicationId(app.getId());
        n.setLinkPath("/projects/" + projectId + "?tab=evidence");
        notificationMapper.insert(n);
    }

    private String resolveDisplayName(Long userId) {
        if (userId == null) return "";
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null) return "";
        if (u.getRealName() != null && !u.getRealName().isBlank()) return u.getRealName().trim();
        return u.getUsername() != null ? u.getUsername() : "";
    }

    private ArchiveApplicationVO toApplicationVO(ProjectArchiveApplication app, Project project, String approverDisplayName) {
        ArchiveApplicationVO vo = new ArchiveApplicationVO();
        vo.setId(app.getId());
        vo.setProjectId(app.getProjectId());
        vo.setProjectName(project != null ? project.getName() : null);
        vo.setApplicantUserId(app.getApplicantUserId());
        vo.setApplicantDisplayName(app.getApplicantUserId() != null ? resolveDisplayName(app.getApplicantUserId()) : null);
        vo.setStatus(app.getStatus());
        vo.setSubmitTime(app.getSubmitTime());
        vo.setApproverUserId(app.getApproverUserId());
        vo.setApproverDisplayName(approverDisplayName);
        vo.setApproveTime(app.getApproveTime());
        vo.setRejectTime(app.getRejectTime());
        vo.setRejectComment(app.getRejectComment());
        vo.setRejectEvidences(null);
        vo.setCreatedAt(app.getCreatedAt());
        vo.setUpdatedAt(app.getUpdatedAt());
        return vo;
    }
}
