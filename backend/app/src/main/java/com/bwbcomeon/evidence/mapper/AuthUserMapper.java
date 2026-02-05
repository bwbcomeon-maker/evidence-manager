package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 用户表Mapper
 */
@Mapper
public interface AuthUserMapper {
    /**
     * 根据ID查询用户
     */
    AuthUser selectById(@Param("id") UUID id);

    /**
     * 根据用户名查询用户
     */
    AuthUser selectByUsername(@Param("username") String username);

    /**
     * 根据ID列表批量查询用户（用于成员列表展示）
     */
    List<AuthUser> selectByIds(@Param("ids") List<UUID> ids);

    /**
     * 查询所有用户
     */
    List<AuthUser> selectAll();

    /**
     * 插入用户
     */
    int insert(AuthUser authUser);

    /**
     * 更新用户
     */
    int update(AuthUser authUser);

    /**
     * 根据ID删除用户
     */
    int deleteById(@Param("id") UUID id);
}
