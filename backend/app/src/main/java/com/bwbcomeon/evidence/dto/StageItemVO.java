package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 阶段内模板行：stage-progress 中 stages[].items[] 元素
 */
@Data
public class StageItemVO {
    private String evidenceTypeCode;
    private String displayName;
    private boolean isRequired;
    private int minCount;
    private int currentCount;
    private boolean completed;       // 行级：currentCount >= minCount
    private String ruleGroup;
    private Boolean groupCompleted;  // 属组时该组是否通过；不属组为 null
    private String groupDisplayName; // 属组时整组展示名
    private Integer sortOrder;
}
