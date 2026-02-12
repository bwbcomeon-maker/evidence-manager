package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 证据计数行：(stage_id, evidence_type_code) → count。
 * 门禁口径（countValidEvidenceByProjectId）仅统计 SUBMITTED/ARCHIVED；
 * 展示口径（countUploadedEvidenceByProjectId）统计 DRAFT/SUBMITTED/ARCHIVED。
 */
@Data
public class EvidenceCountRow {
    private Long stageId;
    private String evidenceTypeCode;
    private long count;
}
