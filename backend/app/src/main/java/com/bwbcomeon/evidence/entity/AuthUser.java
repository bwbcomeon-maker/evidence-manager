package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 系统用户表实体类
 * 
 * @author system
 */
@Data
public class AuthUser {
    
    /**
     * 用户ID（UUID）
     */
    private UUID id;
    
    /**
     * 登录名（唯一登录账号）
     */
    private String username;
    
    /**
     * 显示名称（用户姓名或昵称）
     */
    private String displayName;
    
    /**
     * 邮箱（联系邮箱）
     */
    private String email;
    
    /**
     * 是否启用（用户状态）
     */
    private Boolean isActive;
    
    /**
     * 创建时间
     * 
     * 使用 Instant 类型，因为 PostgreSQL 的 TIMESTAMPTZ 包含时区信息，
     * Instant 是 UTC 时间点，适合存储带时区的时间戳
     */
    private Instant createdAt;
}
