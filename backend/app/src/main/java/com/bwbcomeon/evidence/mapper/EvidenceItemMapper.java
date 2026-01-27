package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.EvidenceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

/**
 * 证据元数据表 Mapper 接口
 */
@Mapper
public interface EvidenceItemMapper {

    /**
     * 根据ID查询
     */
    EvidenceItem selectById(@Param("id") Long id);

    /**
     * 根据项目ID查询证据列表（分页）
     */
    List<EvidenceItem> selectByProjectId(@Param("projectId") Long projectId, 
                                        @Param("limit") Integer limit, 
                                        @Param("offset") Integer offset);

    /**
     * 根据项目ID和状态查询证据列表（分页）
     */
    List<EvidenceItem> selectByProjectIdAndStatus(@Param("projectId") Long projectId, 
                                                  @Param("status") String status, 
                                                  @Param("limit") Integer limit, 
                                                  @Param("offset") Integer offset);

    /**
     * 根据创建人查询证据列表（分页）
     */
    List<EvidenceItem> selectByCreatedBy(@Param("createdBy") UUID createdBy, 
                                         @Param("limit") Integer limit, 
                                         @Param("offset") Integer offset);

    /**
     * 根据存储桶和对象路径查询证据
     */
    EvidenceItem selectByBucketAndObjectKey(@Param("bucket") String bucket, 
                                           @Param("objectKey") String objectKey);

    /**
     * 查询所有证据（分页）
     */
    List<EvidenceItem> selectAll(@Param("limit") Integer limit, @Param("offset") Integer offset);

    /**
     * 统计证据总数
     */
    Long countAll();

    /**
     * 根据项目ID统计证据数量
     */
    Long countByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据项目ID和状态统计证据数量
     */
    Long countByProjectIdAndStatus(@Param("projectId") Long projectId, 
                                   @Param("status") String status);

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
