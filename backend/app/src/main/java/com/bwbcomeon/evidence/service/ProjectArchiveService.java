package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.ArchiveApplicationVO;
import com.bwbcomeon.evidence.dto.ArchiveRejectRequest;

/**
 * 归档审批流服务：申请、通过、退回
 */
public interface ProjectArchiveService {

    /**
     * 提交归档申请：门禁校验 -> 写申请单(PENDING_APPROVAL) -> 项目状态 pending_approval -> 发待办给 PMO/管理员
     */
    ArchiveApplicationVO apply(Long projectId, Long currentUserId, String roleCode);

    /**
     * 审批通过：更新申请单 APPROVED -> 调用原有归档逻辑 -> 发通知给项目经理
     */
    void approve(Long projectId, Long currentUserId, String roleCode);

    /**
     * 退回：写 archive_reject_evidence -> 申请单 REJECTED -> 项目状态 returned -> 发待办给项目经理
     */
    void reject(Long projectId, ArchiveRejectRequest request, Long currentUserId, String roleCode);
}
