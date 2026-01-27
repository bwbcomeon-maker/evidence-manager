package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuditOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

/**
 * 操作审计日志表Mapper
 */
@Mapper
public interface AuditOperationLogMapper {
    /**
     * 根据ID查询日志
     */
    AuditOperationLog selectById(@Param("id") Long id);

    /**
     * 根据操作人查询日志列表
     */
    List<AuditOperationLog> selectByActorUserId(@Param("actorUserId") UUID actorUserId);

    /**
     * 根据目标类型和目标ID查询日志列表
     */
    List<AuditOperationLog> selectByTarget(@Param("targetType") String targetType, @Param("targetId") String targetId);

    /**
     * 根据操作类型查询日志列表
     */
    List<AuditOperationLog> selectByAction(@Param("action") String action);

    /**
     * 查询所有日志
     */
    List<AuditOperationLog> selectAll();

    /**
     * 插入日志
     */
    int insert(AuditOperationLog log);
}
