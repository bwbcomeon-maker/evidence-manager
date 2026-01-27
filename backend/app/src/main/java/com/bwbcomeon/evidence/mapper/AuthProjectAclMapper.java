package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

/**
 * 项目权限表 Mapper 接口
 */
@Mapper
public interface AuthProjectAclMapper {

    /**
     * 根据ID查询
     */
    AuthProjectAcl selectById(@Param("id") Long id);

    /**
     * 根据项目ID和用户ID查询
     */
    AuthProjectAcl selectByProjectIdAndUserId(@Param("projectId") Long projectId, 
                                               @Param("userId") UUID userId);

    /**
     * 根据项目ID查询权限记录列表
     */
    List<AuthProjectAcl> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据用户ID查询权限记录列表
     */
    List<AuthProjectAcl> selectByUserId(@Param("userId") UUID userId);

    /**
     * 根据项目ID和角色查询权限记录列表
     */
    List<AuthProjectAcl> selectByProjectIdAndRole(@Param("projectId") Long projectId, 
                                                  @Param("role") String role);

    /**
     * 查询所有权限记录（分页）
     */
    List<AuthProjectAcl> selectAll(@Param("limit") Integer limit, @Param("offset") Integer offset);

    /**
     * 统计权限记录总数
     */
    Long countAll();

    /**
     * 插入权限记录
     */
    int insert(AuthProjectAcl authProjectAcl);

    /**
     * 更新权限记录
     */
    int update(AuthProjectAcl authProjectAcl);

    /**
     * 根据ID删除权限记录
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据项目ID和用户ID删除权限记录
     */
    int deleteByProjectIdAndUserId(@Param("projectId") Long projectId, 
                                   @Param("userId") UUID userId);
}
