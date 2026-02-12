package com.bwbcomeon.evidence.entity;

import lombok.Data;

/**
 * 阶段证据模板表 stage_evidence_template
 */
@Data
public class StageEvidenceTemplate {
    private Long id;
    private Long stageId;
    private String evidenceTypeCode;
    private String displayName;
    private Boolean isRequired;
    private Integer minCount;
    private Integer sortOrder;
    /** 如 HAS_PROCUREMENT：仅当 project.has_procurement=true 时参与计算 */
    private String requiredWhen;
    /** 同组二选一/多选一共享同一值 */
    private String ruleGroup;
    /** 组内至少需满足的项数 */
    private Integer groupRequiredCount;
}
