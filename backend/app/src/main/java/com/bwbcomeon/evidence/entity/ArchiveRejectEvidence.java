package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 归档退回不符合项表（附件级）archive_reject_evidence
 */
@Data
public class ArchiveRejectEvidence {

    /** 记录ID */
    private Long id;
    /** 所属归档申请单ID */
    private Long applicationId;
    /** 证据ID evidence_item.id */
    private Long evidenceId;
    /** 不符合原因说明 */
    private String rejectComment;
    /** 标注人 sys_user.id */
    private Long createdBy;
    /** 创建时间 */
    private OffsetDateTime createdAt;
}
