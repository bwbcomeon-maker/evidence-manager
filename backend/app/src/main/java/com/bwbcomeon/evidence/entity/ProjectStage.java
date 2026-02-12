package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 项目阶段进度表 project_stage
 */
@Data
public class ProjectStage {
    private Long id;
    private Long projectId;
    private Long stageId;
    /** NOT_STARTED / IN_PROGRESS / COMPLETED */
    private String status;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
