package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 归档退回证据级明细 VO（用于审批历史中的不符合项）
 */
@Data
public class RejectEvidenceDetailVO {

    /** 证据ID */
    private Long evidenceId;
    /** 证据名称（来自 evidence_item.title） */
    private String evidenceName;
    /** 阶段名称（来自 delivery_stage.name） */
    private String stageName;
    /** 该证据的不符合原因 */
    private String rejectComment;
}
