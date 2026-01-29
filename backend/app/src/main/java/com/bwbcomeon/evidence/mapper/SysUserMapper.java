package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统用户表 Mapper
 */
@Mapper
public interface SysUserMapper {

    /** 插入用户 */
    int insert(SysUser sysUser);

    /** 更新用户 */
    int update(SysUser sysUser);

    /** 根据ID查询用户 */
    SysUser selectById(@Param("id") Long id);

    /** 根据登录账号查询用户（仅未删除） */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 分页查询用户（支持 keyword/roleCode/enabled，按创建时间倒序）
     * @param keyword 关键词（匹配 username / real_name）
     * @param roleCode 角色编码
     * @param enabled 是否启用
     * @param offset 偏移量
     * @param limit 每页条数
     */
    List<SysUser> pageQuery(
            @Param("keyword") String keyword,
            @Param("roleCode") String roleCode,
            @Param("enabled") Boolean enabled,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    /** 分页查询总数（条件与 pageQuery 一致） */
    long countPageQuery(
            @Param("keyword") String keyword,
            @Param("roleCode") String roleCode,
            @Param("enabled") Boolean enabled
    );

    /** 设置启用状态 */
    int setEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    /** 重置密码（更新 password_hash） */
    int resetPassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /** 逻辑删除 */
    int logicalDelete(@Param("id") Long id);

    /** 按登录账号统计（含已删除，用于创建时唯一性校验） */
    long countByUsername(@Param("username") String username);

    /** 统计指定角色且未删除的用户数（用于“不能删除唯一管理员”校验） */
    long countByRoleCodeAndNotDeleted(@Param("roleCode") String roleCode);
}
