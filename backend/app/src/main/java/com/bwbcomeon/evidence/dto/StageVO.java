package com.bwbcomeon.evidence.dto;

import lombok.Data;
import java.util.List;

/**
 * 阶段进度：stage-progress 中 stages[] 元素
 */
@Data
public class StageVO {
    private Long stageId;
    private String stageCode;
    private String stageName;
    private String stageDescription;
    private int itemCount;           // y，参与完成度/归档口径
    private int completedCount;      // x，参与完成度/归档口径
    /** 当阶段无必填项（如 S2 在无采购时）时，用于阶段头部展示的实际上传数量；为空则用 itemCount/completedCount */
    private Integer displayItemCount;
    private Integer displayCompletedCount;
    private int completionPercent;
    private String healthStatus;     // COMPLETE | PARTIAL | NOT_STARTED
    private boolean stageCompleted;  // project_stage.status == COMPLETED
    private boolean canComplete;     // x == y
    private List<StageItemVO> items;
}
