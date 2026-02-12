package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 阶段内模板行：stage-progress 中 stages[].items[] 元素
 * <p>
 * 双口径模型：
 * - currentCount / completed → 门禁口径（仅 SUBMITTED + ARCHIVED），驱动阶段完成与归档判断
 * - uploadCount → 展示口径（DRAFT + SUBMITTED + ARCHIVED），仅用于 UI 展示"已上传数量"
 */
@Data
public class StageItemVO {
    private String evidenceTypeCode;
    private String displayName;
    private boolean isRequired;
    private int minCount;
    /** 门禁口径：仅 SUBMITTED + ARCHIVED 的有效证据数，驱动 completed / canComplete / canArchive */
    private int currentCount;
    /** 展示口径：DRAFT + SUBMITTED + ARCHIVED 的已上传数，仅用于 UI 展示 */
    private int uploadCount;
    private boolean completed;       // 行级：currentCount >= minCount（门禁口径）
    private String ruleGroup;
    private Boolean groupCompleted;  // 属组时该组是否通过；不属组为 null
    private String groupDisplayName; // 属组时整组展示名
    private Integer sortOrder;
}
