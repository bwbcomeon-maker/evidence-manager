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
    private int itemCount;           // y
    private int completedCount;      // x
    private int completionPercent;
    private String healthStatus;     // COMPLETE | PARTIAL | NOT_STARTED
    private boolean stageCompleted;  // project_stage.status == COMPLETED
    private boolean canComplete;     // x == y
    private List<StageItemVO> items;
}
