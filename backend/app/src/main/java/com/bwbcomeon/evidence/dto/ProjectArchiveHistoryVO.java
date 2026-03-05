package com.bwbcomeon.evidence.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 归档审批历史记录 VO（按申请单维度，含该次退回的证据级不符合项）
 */
@Data
public class ProjectArchiveHistoryVO {

    /** 申请单ID */
    private Long applicationId;
    /** 申请单状态：PENDING_APPROVAL / APPROVED / REJECTED */
    private String status;
    /** 申请人姓名（展示用） */
    private String applicantDisplayName;
    /** 审批人姓名（通过或退回时的操作人，展示用） */
    private String approverDisplayName;
    /** 提交时间 */
    private OffsetDateTime submitTime;
    /** 操作时间：通过时为 approve_time，退回时为 reject_time，待审批时为 null */
    private OffsetDateTime operationTime;
    /** 总体退回意见（仅 REJECTED 时有值） */
    private String rejectComment;
    /** 该次申请下的证据级不符合项列表（仅退回时可能有数据） */
    private List<RejectEvidenceDetailVO> rejectEvidences;
}
