package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.EvidenceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

/**
 * 证据元数据表 Mapper
 */
@Mapper
public interface EvidenceItemMapper {
    /**
     * 根据ID查询证据
     */
    EvidenceItem selectById(@Param("id") Long id);

    /**
     * 根据项目ID查询证据列表
     */
    List<EvidenceItem> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据项目ID和状态查询证据列表
     */
    List<EvidenceItem> selectByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") String status);

    /**
     * 根据创建人查询证据列表
     */
    List<EvidenceItem> selectByCreatedBy(@Param("createdBy") UUID createdBy);

    /**
     * 查询所有证据
     */
    List<EvidenceItem> selectAll();

    /**
     * 插入证据
     */
    int insert(EvidenceItem evidenceItem);

    /**
     * 更新证据
     */
    int update(EvidenceItem evidenceItem);

    /**
     * 根据ID删除证据
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据项目ID和条件查询证据列表（支持名称模糊匹配、状态过滤、业务类型过滤、文件类型过滤）
     * @param projectId 项目ID
     * @param nameLike 证据名称模糊匹配（可选）
     * @param status 证据状态（可选）
     * @param bizType 业务证据类型（可选，如PLAN/REPORT/MINUTES/TEST/ACCEPTANCE/OTHER）
     * @param contentType 文件类型（MIME类型，可选，如application/pdf）
     * @return 证据列表
     */
    List<EvidenceItem> selectByProjectIdWithFilters(
            @Param("projectId") Long projectId,
            @Param("nameLike") String nameLike,
            @Param("status") String status,
            @Param("bizType") String bizType,
            @Param("contentType") String contentType
    );
}
