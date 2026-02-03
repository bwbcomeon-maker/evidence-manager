package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

/**
 * 项目表Mapper
 */
@Mapper
public interface ProjectMapper {
    /**
     * 根据ID查询项目
     */
    Project selectById(@Param("id") Long id);

    /**
     * 根据项目令号查询项目
     */
    Project selectByCode(@Param("code") String code);

    /**
     * 根据创建人查询项目列表
     */
    List<Project> selectByCreatedBy(@Param("createdBy") UUID createdBy);

    /**
     * 查询所有项目
     */
    List<Project> selectAll();

    /**
     * 根据ID列表查询项目（保持 created_at 倒序）
     */
    List<Project> selectByIds(@Param("ids") List<Long> ids);

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
