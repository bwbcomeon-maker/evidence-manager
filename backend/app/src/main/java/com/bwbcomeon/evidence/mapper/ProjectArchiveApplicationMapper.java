package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.ProjectArchiveApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 归档申请单 Mapper
 */
@Mapper
public interface ProjectArchiveApplicationMapper {

    int insert(ProjectArchiveApplication entity);

    ProjectArchiveApplication selectById(@Param("id") Long id);

    /** 按项目ID查询当前待审批申请（status = PENDING_APPROVAL） */
    ProjectArchiveApplication selectPendingByProjectId(@Param("projectId") Long projectId);

    /** 按项目ID查询最新一条 REJECTED 申请（按 reject_time 倒序，用于返回退回原因） */
    ProjectArchiveApplication selectLatestRejectedByProjectId(@Param("projectId") Long projectId);

    int update(ProjectArchiveApplication entity);

    /** 按申请单ID更新状态及审批/退回相关字段 */
    int updateStatusAndAudit(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("approverUserId") Long approverUserId,
            @Param("approveTime") java.time.OffsetDateTime approveTime,
            @Param("rejectTime") java.time.OffsetDateTime rejectTime,
            @Param("rejectComment") String rejectComment
    );
}
