package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

/**
 * 项目表 Mapper 接口
 */
@Mapper
public interface ProjectMapper {

    /**
     * 根据ID查询
     */
    Project selectById(@Param("id") Long id);

    /**
     * 根据项目编号查询
     */
    Project selectByCode(@Param("code") String code);

    /**
     * 根据创建人查询项目列表（分页）
     */
    List<Project> selectByCreatedBy(@Param("createdBy") UUID createdBy, 
                                    @Param("limit") Integer limit, 
                                    @Param("offset") Integer offset);

    /**
     * 根据状态查询项目列表（分页）
     */
    List<Project> selectByStatus(@Param("status") String status, 
                                 @Param("limit") Integer limit, 
                                 @Param("offset") Integer offset);

    /**
     * 查询所有项目（分页）
     */
    List<Project> selectAll(@Param("limit") Integer limit, @Param("offset") Integer offset);

    /**
     * 统计项目总数
     */
    Long countAll();

    /**
     * 根据创建人统计项目数量
     */
    Long countByCreatedBy(@Param("createdBy") UUID createdBy);

    /**
     * 根据状态统计项目数量
     */
    Long countByStatus(@Param("status") String status);

    /**
     * 插入项目
     */
    int insert(Project project);

    /**
     * 更新项目
     */
    int update(Project project);

    /**
     * 根据ID删除项目
     */
    int deleteById(@Param("id") Long id);
}
