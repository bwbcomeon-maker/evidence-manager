package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审计日志表 Mapper
 */
@Mapper
public interface AuditLogMapper {

    /** 插入审计日志 */
    int insert(AuditLog auditLog);

    /**
     * 分页查询审计日志（按操作时间倒序）
     * @param offset 偏移量
     * @param limit 每页条数
     */
    List<AuditLog> pageQuery(@Param("offset") long offset, @Param("limit") long limit);

    /** 分页查询总数 */
    long countPageQuery();
}
