package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 系统用户表 Mapper 接口
 * 
 * @author system
 */
@Mapper
public interface AuthUserMapper {
    
    /**
     * 根据ID查询用户
     * 
     * @param id 用户ID
     * @return 用户实体
     */
    AuthUser selectById(@Param("id") UUID id);
    
    /**
     * 根据用户名查询用户
     * 
     * @param username 用户名
     * @return 用户实体
     */
    AuthUser selectByUsername(@Param("username") String username);
    
    /**
     * 查询所有用户（分页）
     * 
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 用户列表
     */
    List<AuthUser> selectAll(@Param("offset") Long offset, @Param("limit") Integer limit);
    
    /**
     * 统计用户总数
     * 
     * @return 用户总数
     */
    Long countAll();
    
    /**
     * 插入用户
     * 
     * @param user 用户实体
     * @return 影响行数
     */
    int insert(AuthUser user);
    
    /**
     * 更新用户
     * 
     * @param user 用户实体
     * @return 影响行数
     */
    int update(AuthUser user);
    
    /**
     * 根据ID删除用户（物理删除，实际业务中可能不需要）
     * 
     * @param id 用户ID
     * @return 影响行数
     */
    int deleteById(@Param("id") UUID id);
}
