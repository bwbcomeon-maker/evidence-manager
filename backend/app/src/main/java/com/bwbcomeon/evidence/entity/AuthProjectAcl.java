package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 项目权限表实体类
 * 
 * @author system
 */
@Data
public class AuthProjectAcl {
    
    /**
     * 记录ID
     */
    private Long id;
    
    /**
     * 项目ID（所属项目）
     */
    private Long projectId;
    
    /**
     * 用户ID（授权用户）
     */
    private UUID userId;
    
    /**
     * 项目角色：owner-项目负责人, editor-项目成员, viewer-查看人员
     */
    private String role;
    
    /**
     * 授权时间
     * 
     * 使用 Instant 类型，因为 PostgreSQL 的 TIMESTAMPTZ 包含时区信息，
     * Instant 是 UTC 时间点，适合存储带时区的时间戳
     */
    private Instant createdAt;
}
