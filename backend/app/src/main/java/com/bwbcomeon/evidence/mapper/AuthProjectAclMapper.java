package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 项目权限表Mapper
 */
@Mapper
public interface AuthProjectAclMapper {
    /**
     * 根据ID查询权限记录
     */
    AuthProjectAcl selectById(@Param("id") Long id);

    /**
     * 根据项目ID查询权限列表
     */
    List<AuthProjectAcl> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据用户ID查询权限列表
     */
    List<AuthProjectAcl> selectBySysUserId(@Param("sysUserId") Long sysUserId);

    /**
     * 根据项目ID和用户ID查询权限
     */
    AuthProjectAcl selectByProjectIdAndSysUserId(@Param("projectId") Long projectId, @Param("sysUserId") Long sysUserId);

    /**
     * 插入权限记录
     */
    int insert(AuthProjectAcl acl);

    /**
     * 更新权限记录
     */
    int update(AuthProjectAcl acl);

    /**
     * 根据ID删除权限记录
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据项目ID和用户ID删除权限记录
     */
    int deleteByProjectIdAndSysUserId(@Param("projectId") Long projectId, @Param("sysUserId") Long sysUserId);
}
