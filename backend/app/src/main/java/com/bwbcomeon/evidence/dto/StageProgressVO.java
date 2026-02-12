package com.bwbcomeon.evidence.dto;

import lombok.Data;
import java.util.List;

/**
 * GET /api/projects/{projectId}/stage-progress 响应；归档失败时也复用 blockedByStages / blockedByRequiredItems
 */
@Data
public class StageProgressVO {
    private int overallCompletionPercent;
    private List<String> keyMissing;
    private boolean canArchive;
    private String archiveBlockReason;
    private List<StageVO> stages;
    private String projectName;
    private String projectStatus;
    private Boolean hasProcurement;
    /** 归档门禁失败时：未达 100% 或未标记完成的阶段 code 列表 */
    private List<String> blockedByStages;
    /** 归档门禁失败时：未满足的必填项（便于前端跳转） */
    private List<BlockedByItemVO> blockedByRequiredItems;
}
