package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.ProjectStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectStageMapper {
    List<ProjectStage> selectByProjectId(@Param("projectId") Long projectId);

    ProjectStage selectByProjectIdAndStageId(@Param("projectId") Long projectId, @Param("stageId") Long stageId);

    int insert(ProjectStage projectStage);

    int updateStatus(@Param("projectId") Long projectId, @Param("stageId") Long stageId,
                     @Param("status") String status, @Param("completedAt") java.time.OffsetDateTime completedAt);
}
