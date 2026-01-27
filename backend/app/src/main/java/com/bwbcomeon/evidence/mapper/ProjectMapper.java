package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 项目表 Mapper 接口
 * 
 * @author system
 */
@Mapper
public interface ProjectMapper {
    
    /**
     * 根据ID查询项目
     * 
     * @param id 项目ID
     * @return 项目实体
     */
    Project selectById(@Param("id") Long id);
    
    /**
     * 根据项目编号查询项目
     * 
     * @param code 项目编号
     * @return 项目实体
     */
    Project selectByCode(@Param("code") String code);
    
    /**
     * 根据创建人查询项目列表（分页）
     * 
     * @param createdBy 创建人ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 项目列表
     */
    List<Project> selectByCreatedBy(@Param("createdBy") UUID createdBy, 
                                     @Param("offset") Long offset, 
                                     @Param("limit") Integer limit);
    
    /**
     * 根据状态查询项目列表（分页）
     * 
     * @param status 项目状态
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 项目列表
     */
    List<Project> selectByStatus(@Param("status") String status, 
                                  @Param("offset") Long offset, 
                                  @Param("limit") Integer limit);
    
    /**
     * 查询所有项目（分页）
     * 
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 项目列表
     */
    List<Project> selectAll(@Param("offset") Long offset, @Param("limit") Integer limit);
    
    /**
     * 统计项目总数
     * 
     * @return 项目总数
     */
    Long countAll();
    
    /**
     * 根据创建人统计项目数量
     * 
     * @param createdBy 创建人ID
     * @return 项目数量
     */
    Long countByCreatedBy(@Param("createdBy") UUID createdBy);
    
    /**
     * 根据状态统计项目数量
     * 
     * @param status 项目状态
     * @return 项目数量
     */
    Long countByStatus(@Param("status") String status);
    
    /**
     * 插入项目
     * 
     * @param project 项目实体
     * @return 影响行数
     */
    int insert(Project project);
    
    /**
     * 更新项目
     * 
     * @param project 项目实体
     * @return 影响行数
     */
    int update(Project project);
    
    /**
     * 根据ID删除项目（物理删除，实际业务中可能不需要）
     * 
     * @param id 项目ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
