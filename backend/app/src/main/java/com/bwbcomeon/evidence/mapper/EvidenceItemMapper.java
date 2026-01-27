package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.EvidenceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 证据元数据表 Mapper 接口
 * 
 * @author system
 */
@Mapper
public interface EvidenceItemMapper {
    
    /**
     * 根据ID查询证据
     * 
     * @param id 证据ID
     * @return 证据实体
     */
    EvidenceItem selectById(@Param("id") Long id);
    
    /**
     * 根据项目ID查询证据列表（分页）
     * 
     * @param projectId 项目ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 证据列表
     */
    List<EvidenceItem> selectByProjectId(@Param("projectId") Long projectId, 
                                         @Param("offset") Long offset, 
                                         @Param("limit") Integer limit);
    
    /**
     * 根据项目ID和状态查询证据列表（分页）
     * 
     * @param projectId 项目ID
     * @param status 状态
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 证据列表
     */
    List<EvidenceItem> selectByProjectIdAndStatus(@Param("projectId") Long projectId, 
                                                    @Param("status") String status,
                                                    @Param("offset") Long offset, 
                                                    @Param("limit") Integer limit);
    
    /**
     * 根据创建人查询证据列表（分页）
     * 
     * @param createdBy 创建人ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 证据列表
     */
    List<EvidenceItem> selectByCreatedBy(@Param("createdBy") UUID createdBy, 
                                         @Param("offset") Long offset, 
                                         @Param("limit") Integer limit);
    
    /**
     * 根据存储桶和对象路径查询证据
     * 
     * @param bucket 存储桶
     * @param objectKey 对象路径
     * @return 证据实体
     */
    EvidenceItem selectByBucketAndObjectKey(@Param("bucket") String bucket, 
                                            @Param("objectKey") String objectKey);
    
    /**
     * 查询所有证据（分页）
     * 
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 证据列表
     */
    List<EvidenceItem> selectAll(@Param("offset") Long offset, @Param("limit") Integer limit);
    
    /**
     * 统计证据总数
     * 
     * @return 证据总数
     */
    Long countAll();
    
    /**
     * 根据项目ID统计证据数量
     * 
     * @param projectId 项目ID
     * @return 证据数量
     */
    Long countByProjectId(@Param("projectId") Long projectId);
    
    /**
     * 根据项目ID和状态统计证据数量
     * 
     * @param projectId 项目ID
     * @param status 状态
     * @return 证据数量
     */
    Long countByProjectIdAndStatus(@Param("projectId") Long projectId, 
                                    @Param("status") String status);
    
    /**
     * 插入证据
     * 
     * @param evidenceItem 证据实体
     * @return 影响行数
     */
    int insert(EvidenceItem evidenceItem);
    
    /**
     * 更新证据
     * 
     * @param evidenceItem 证据实体
     * @return 影响行数
     */
    int update(EvidenceItem evidenceItem);
    
    /**
     * 根据ID删除证据（物理删除，实际业务中可能不需要）
     * 
     * @param id 证据ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
