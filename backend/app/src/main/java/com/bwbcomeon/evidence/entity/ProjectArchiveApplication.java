package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 归档申请单表 project_archive_application
 */
@Data
public class ProjectArchiveApplication {

    /** 申请单ID */
    private Long id;
    /** 所属项目ID */
    private Long projectId;
    /** 申请人（项目经理）sys_user.id */
    private Long applicantUserId;
    /** 状态：PENDING_APPROVAL / APPROVED / REJECTED */
    private String status;
    /** 提交时间 */
    private OffsetDateTime submitTime;
    /** 审批人（PMO/管理员）sys_user.id */
    private Long approverUserId;
    /** 审批通过时间 */
    private OffsetDateTime approveTime;
    /** 退回时间 */
    private OffsetDateTime rejectTime;
    /** 退回意见全文 */
    private String rejectComment;
    /** 创建时间 */
    private OffsetDateTime createdAt;
    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
