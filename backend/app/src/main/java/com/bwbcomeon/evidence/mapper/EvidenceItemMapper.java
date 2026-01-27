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
}
