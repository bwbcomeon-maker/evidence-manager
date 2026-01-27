package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuditOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 操作审计日志表 Mapper 接口
 */
@Mapper
public interface AuditOperationLogMapper {

    /**
     * 根据ID查询
     */
    AuditOperationLog selectById(@Param("id") Long id);

    /**
     * 根据操作人查询审计日志列表（分页）
     */
    List<AuditOperationLog> selectByActorUserId(@Param("actorUserId") UUID actorUserId, 
                                                @Param("limit") Integer limit, 
                                                @Param("offset") Integer offset);

    /**
     * 根据操作类型查询审计日志列表（分页）
     */
    List<AuditOperationLog> selectByAction(@Param("action") String action, 
                                           @Param("limit") Integer limit, 
                                           @Param("offset") Integer offset);

    /**
     * 根据目标类型和目标ID查询审计日志列表（分页）
     */
    List<AuditOperationLog> selectByTarget(@Param("targetType") String targetType, 
                                            @Param("targetId") String targetId, 
                                            @Param("limit") Integer limit, 
                                            @Param("offset") Integer offset);

    /**
     * 根据时间范围查询审计日志列表（分页）
     */
    List<AuditOperationLog> selectByTimeRange(@Param("startTime") OffsetDateTime startTime, 
                                                @Param("endTime") OffsetDateTime endTime, 
                                                @Param("limit") Integer limit, 
                                                @Param("offset") Integer offset);

    /**
     * 查询所有审计日志（分页）
     */
    List<AuditOperationLog> selectAll(@Param("limit") Integer limit, @Param("offset") Integer offset);

    /**
     * 统计审计日志总数
     */
    Long countAll();

    /**
     * 根据操作人统计审计日志数量
     */
    Long countByActorUserId(@Param("actorUserId") UUID actorUserId);

    /**
     * 插入审计日志
     */
    int insert(AuditOperationLog auditOperationLog);

    /**
     * 根据ID删除审计日志
     */
    int deleteById(@Param("id") Long id);
}
