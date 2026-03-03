package com.bwbcomeon.evidence.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 归档申请单 VO（列表/详情）
 */
@Data
public class ArchiveApplicationVO {

    private Long id;
    private Long projectId;
    private String projectName;
    private Long applicantUserId;
    private String applicantDisplayName;
    private String status;
    private OffsetDateTime submitTime;
    private Long approverUserId;
    private String approverDisplayName;
    private OffsetDateTime approveTime;
    private OffsetDateTime rejectTime;
    private String rejectComment;
    /** 不符合项列表（退回时） */
    private List<RejectEvidenceItemVO> rejectEvidences;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
