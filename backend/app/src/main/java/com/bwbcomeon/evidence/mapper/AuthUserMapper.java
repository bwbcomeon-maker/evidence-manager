package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

/**
 * 用户表 Mapper 接口
 */
@Mapper
public interface AuthUserMapper {

    /**
     * 根据ID查询
     */
    AuthUser selectById(@Param("id") UUID id);

    /**
     * 根据用户名查询
     */
    AuthUser selectByUsername(@Param("username") String username);

    /**
     * 查询所有用户（分页）
     */
    List<AuthUser> selectAll(@Param("limit") Integer limit, @Param("offset") Integer offset);

    /**
     * 统计用户总数
     */
    Long countAll();

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
