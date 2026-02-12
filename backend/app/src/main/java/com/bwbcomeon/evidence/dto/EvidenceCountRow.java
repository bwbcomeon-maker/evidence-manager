package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 有效证据计数：(project_id, stage_id, evidence_type_code) 下的 SUBMITTED/ARCHIVED 条数
 */
@Data
public class EvidenceCountRow {
    private Long stageId;
    private String evidenceTypeCode;
    private long count;
}
