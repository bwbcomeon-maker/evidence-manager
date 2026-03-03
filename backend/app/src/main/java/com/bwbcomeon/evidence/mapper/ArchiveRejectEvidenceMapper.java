package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.ArchiveRejectEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 归档退回不符合项 Mapper
 */
@Mapper
public interface ArchiveRejectEvidenceMapper {

    int insert(ArchiveRejectEvidence entity);

    /** 按申请单ID查询不符合项列表 */
    List<ArchiveRejectEvidence> selectByApplicationId(@Param("applicationId") Long applicationId);

    /** 按申请单ID删除（退回时先删再插或覆盖） */
    int deleteByApplicationId(@Param("applicationId") Long applicationId);
}
