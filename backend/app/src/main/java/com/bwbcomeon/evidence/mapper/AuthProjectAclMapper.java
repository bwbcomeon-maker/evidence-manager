package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 项目权限表 Mapper 接口
 * 
 * @author system
 */
@Mapper
public interface AuthProjectAclMapper {
    
    /**
     * 根据ID查询权限记录
     * 
     * @param id 记录ID
     * @return 权限记录实体
     */
    AuthProjectAcl selectById(@Param("id") Long id);
    
    /**
     * 根据项目ID和用户ID查询权限记录
     * 
     * @param projectId 项目ID
     * @param userId 用户ID
     * @return 权限记录实体
     */
    AuthProjectAcl selectByProjectIdAndUserId(@Param("projectId") Long projectId, 
                                               @Param("userId") UUID userId);
    
    /**
     * 根据项目ID查询权限记录列表
     * 
     * @param projectId 项目ID
     * @return 权限记录列表
     */
    List<AuthProjectAcl> selectByProjectId(@Param("projectId") Long projectId);
    
    /**
     * 根据用户ID查询权限记录列表
     * 
     * @param userId 用户ID
     * @return 权限记录列表
     */
    List<AuthProjectAcl> selectByUserId(@Param("userId") UUID userId);
    
    /**
     * 根据项目ID和角色查询权限记录列表
     * 
     * @param projectId 项目ID
     * @param role 角色
     * @return 权限记录列表
     */
    List<AuthProjectAcl> selectByProjectIdAndRole(@Param("projectId") Long projectId, 
                                                    @Param("role") String role);
    
    /**
     * 查询所有权限记录（分页）
     * 
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 权限记录列表
     */
    List<AuthProjectAcl> selectAll(@Param("offset") Long offset, @Param("limit") Integer limit);
    
    /**
     * 统计权限记录总数
     * 
     * @return 权限记录总数
     */
    Long countAll();
    
    /**
     * 插入权限记录
     * 
     * @param acl 权限记录实体
     * @return 影响行数
     */
    int insert(AuthProjectAcl acl);
    
    /**
     * 更新权限记录
     * 
     * @param acl 权限记录实体
     * @return 影响行数
     */
    int update(AuthProjectAcl acl);
    
    /**
     * 根据ID删除权限记录
     * 
     * @param id 记录ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 根据项目ID和用户ID删除权限记录
     * 
     * @param projectId 项目ID
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByProjectIdAndUserId(@Param("projectId") Long projectId, 
                                    @Param("userId") UUID userId);
}
