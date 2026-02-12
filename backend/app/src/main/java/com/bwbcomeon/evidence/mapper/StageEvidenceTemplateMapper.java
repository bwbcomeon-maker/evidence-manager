package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.StageEvidenceTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StageEvidenceTemplateMapper {
    List<StageEvidenceTemplate> selectAll();

    List<StageEvidenceTemplate> selectByStageId(@Param("stageId") Long stageId);

    StageEvidenceTemplate selectByStageIdAndEvidenceTypeCode(@Param("stageId") Long stageId, @Param("evidenceTypeCode") String evidenceTypeCode);
}
