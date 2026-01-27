package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuditOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 操作审计日志表 Mapper 接口
 * 
 * @author system
 */
@Mapper
public interface AuditOperationLogMapper {
    
    /**
     * 根据ID查询审计日志
     * 
     * @param id 日志ID
     * @return 审计日志实体
     */
    AuditOperationLog selectById(@Param("id") Long id);
    
    /**
     * 根据操作人查询审计日志列表（分页）
     * 
     * @param actorUserId 操作人ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 审计日志列表
     */
    List<AuditOperationLog> selectByActorUserId(@Param("actorUserId") UUID actorUserId, 
                                                 @Param("offset") Long offset, 
                                                 @Param("limit") Integer limit);
    
    /**
     * 根据操作类型查询审计日志列表（分页）
     * 
     * @param action 操作类型
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 审计日志列表
     */
    List<AuditOperationLog> selectByAction(@Param("action") String action, 
                                           @Param("offset") Long offset, 
                                           @Param("limit") Integer limit);
    
    /**
     * 根据目标类型和目标ID查询审计日志列表（分页）
     * 
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 审计日志列表
     */
    List<AuditOperationLog> selectByTarget(@Param("targetType") String targetType, 
                                            @Param("targetId") String targetId,
                                            @Param("offset") Long offset, 
                                            @Param("limit") Integer limit);
    
    /**
     * 根据时间范围查询审计日志列表（分页）
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 审计日志列表
     */
    List<AuditOperationLog> selectByTimeRange(@Param("startTime") Instant startTime, 
                                               @Param("endTime") Instant endTime,
                                               @Param("offset") Long offset, 
                                               @Param("limit") Integer limit);
    
    /**
     * 查询所有审计日志（分页）
     * 
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 审计日志列表
     */
    List<AuditOperationLog> selectAll(@Param("offset") Long offset, @Param("limit") Integer limit);
    
    /**
     * 统计审计日志总数
     * 
     * @return 审计日志总数
     */
    Long countAll();
    
    /**
     * 根据操作人统计审计日志数量
     * 
     * @param actorUserId 操作人ID
     * @return 审计日志数量
     */
    Long countByActorUserId(@Param("actorUserId") UUID actorUserId);
    
    /**
     * 插入审计日志
     * 
     * @param log 审计日志实体
     * @return 影响行数
     */
    int insert(AuditOperationLog log);
    
    /**
     * 根据ID删除审计日志（物理删除，实际业务中可能不需要）
     * 
     * @param id 日志ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
